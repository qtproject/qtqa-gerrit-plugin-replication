// Copyright (C) 2020 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.googlesource.gerrit.plugins.replication;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;
import com.googlesource.gerrit.plugins.replication.ReplicationTasksStorage.ReplicateRefUpdate;
import java.net.URISyntaxException;
import java.nio.file.FileSystem;
import java.nio.file.Path;
import org.eclipse.jgit.transport.URIish;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class ReplicationTasksStorageTest {
  protected static final String PROJECT = "myProject";
  protected static final String REF = "myRef";
  protected static final String REMOTE = "myDest";
  protected static final URIish URISH = getUrish("http://example.com/" + PROJECT + ".git");
  protected static final ReplicateRefUpdate REF_UPDATE =
      ReplicateRefUpdate.create(PROJECT, REF, URISH, REMOTE);

  protected ReplicationTasksStorage storage;
  protected FileSystem fileSystem;
  protected Path storageSite;
  protected UriUpdates uriUpdates;

  @Before
  public void setUp() throws Exception {
    fileSystem = Jimfs.newFileSystem(Configuration.unix());
    storageSite = fileSystem.getPath("replication_site");
    storage = new ReplicationTasksStorage(storageSite);
    uriUpdates = TestUriUpdates.create(REF_UPDATE);
  }

  @After
  public void tearDown() throws Exception {
    fileSystem.close();
  }

  @Test
  public void canListEmptyStorage() throws Exception {
    assertThat(storage.listWaiting()).isEmpty();
    assertThat(storage.listRunning()).isEmpty();
  }

  @Test
  public void canListWaitingUpdate() throws Exception {
    storage.create(REF_UPDATE);
    assertThat(storage.listWaiting()).containsExactly(REF_UPDATE);
  }

  @Test
  public void canStartWaitingUpdate() throws Exception {
    storage.create(REF_UPDATE);
    storage.start(uriUpdates);
    assertThat(storage.listWaiting()).isEmpty();
    assertThat(storage.listRunning()).containsExactly(REF_UPDATE);
  }

  @Test
  public void canFinishRunningUpdate() throws Exception {
    storage.create(REF_UPDATE);
    storage.start(uriUpdates);
    storage.finish(uriUpdates);
    assertNoIncompleteTasks(storage);
  }

  @Test
  public void instancesOfTheSameStorageHaveTheSameElements() throws Exception {
    ReplicationTasksStorage persistedView = new ReplicationTasksStorage(storageSite);

    assertThat(storage.listWaiting()).isEmpty();
    assertThat(persistedView.listWaiting()).isEmpty();

    storage.create(REF_UPDATE);
    assertThat(storage.listWaiting()).containsExactly(REF_UPDATE);
    assertThat(persistedView.listWaiting()).containsExactly(REF_UPDATE);

    storage.start(uriUpdates);
    assertThat(storage.listWaiting()).isEmpty();
    assertThat(persistedView.listWaiting()).isEmpty();
    assertThat(storage.listRunning()).containsExactly(REF_UPDATE);
    assertThat(persistedView.listRunning()).containsExactly(REF_UPDATE);

    storage.finish(uriUpdates);
    assertThat(storage.listRunning()).isEmpty();
    assertThat(persistedView.listRunning()).isEmpty();
  }

  @Test
  public void sameRefUpdateCreatedTwiceIsStoredOnce() throws Exception {
    String key = storage.create(REF_UPDATE);
    String secondKey = storage.create(REF_UPDATE);
    assertEquals(key, secondKey);
    assertThat(storage.listWaiting()).containsExactly(REF_UPDATE);
  }

  @Test
  public void canCreateDifferentUris() throws Exception {
    ReplicateRefUpdate updateB =
        ReplicateRefUpdate.create(
            PROJECT,
            REF,
            getUrish("ssh://example.com/" + PROJECT + ".git"), // uses ssh not http
            REMOTE);

    String keyA = storage.create(REF_UPDATE);
    String keyB = storage.create(updateB);
    assertThat(storage.listWaiting()).hasSize(2);
    assertNotEquals(keyA, keyB);
  }

  @Test
  public void canStartDifferentUris() throws Exception {
    ReplicateRefUpdate updateB =
        ReplicateRefUpdate.create(
            PROJECT,
            REF,
            getUrish("ssh://example.com/" + PROJECT + ".git"), // uses ssh not http
            REMOTE);
    UriUpdates uriUpdatesB = TestUriUpdates.create(updateB);
    storage.create(REF_UPDATE);
    storage.create(updateB);

    storage.start(uriUpdates);
    assertThat(storage.listWaiting()).containsExactly(updateB);
    assertThat(storage.listRunning()).containsExactly(REF_UPDATE);

    storage.start(uriUpdatesB);
    assertThat(storage.listWaiting()).isEmpty();
    assertThat(storage.listRunning()).containsExactly(REF_UPDATE, updateB);
  }

  @Test
  public void canFinishDifferentUris() throws Exception {
    ReplicateRefUpdate updateB =
        ReplicateRefUpdate.create(
            PROJECT,
            REF,
            getUrish("ssh://example.com/" + PROJECT + ".git"), // uses ssh not http
            REMOTE);
    UriUpdates uriUpdatesB = TestUriUpdates.create(updateB);
    storage.create(REF_UPDATE);
    storage.create(updateB);
    storage.start(uriUpdates);
    storage.start(uriUpdatesB);

    storage.finish(uriUpdates);
    assertThat(storage.listRunning()).containsExactly(updateB);

    storage.finish(uriUpdatesB);
    assertThat(storage.listRunning()).isEmpty();
  }

  @Test
  public void differentUrisCreatedTwiceIsStoredOnce() throws Exception {
    ReplicateRefUpdate updateB =
        ReplicateRefUpdate.create(
            PROJECT,
            REF,
            getUrish("ssh://example.com/" + PROJECT + ".git"), // uses ssh not http
            REMOTE);

    storage.create(REF_UPDATE);
    storage.create(updateB);
    storage.create(REF_UPDATE);
    storage.create(updateB);
    assertThat(storage.listWaiting()).hasSize(2);
  }

  @Test
  public void canCreateMulipleRefsForSameUri() throws Exception {
    ReplicateRefUpdate refA = ReplicateRefUpdate.create(PROJECT, "refA", URISH, REMOTE);
    ReplicateRefUpdate refB = ReplicateRefUpdate.create(PROJECT, "refB", URISH, REMOTE);

    String keyA = storage.create(refA);
    String keyB = storage.create(refB);
    assertThat(storage.listWaiting()).hasSize(2);
    assertNotEquals(keyA, keyB);
  }

  @Test
  public void canFinishMulipleRefsForSameUri() throws Exception {
    ReplicateRefUpdate refUpdateA = ReplicateRefUpdate.create(PROJECT, "refA", URISH, REMOTE);
    ReplicateRefUpdate refUpdateB = ReplicateRefUpdate.create(PROJECT, "refB", URISH, REMOTE);
    UriUpdates uriUpdatesA = TestUriUpdates.create(refUpdateA);
    UriUpdates uriUpdatesB = TestUriUpdates.create(refUpdateB);
    storage.create(refUpdateA);
    storage.create(refUpdateB);
    storage.start(uriUpdatesA);
    storage.start(uriUpdatesB);

    storage.finish(uriUpdatesA);
    assertThat(storage.listRunning()).containsExactly(refUpdateB);

    storage.finish(uriUpdatesB);
    assertThat(storage.listRunning()).isEmpty();
  }

  @Test
  public void canResetUpdate() throws Exception {
    storage.create(REF_UPDATE);
    storage.start(uriUpdates);

    storage.reset(uriUpdates);
    assertThat(storage.listWaiting()).containsExactly(REF_UPDATE);
    assertThat(storage.listRunning()).isEmpty();
  }

  @Test
  public void canCompleteResetUpdate() throws Exception {
    storage.create(REF_UPDATE);
    storage.start(uriUpdates);
    storage.reset(uriUpdates);

    storage.start(uriUpdates);
    assertThat(storage.listRunning()).containsExactly(REF_UPDATE);
    assertThat(storage.listWaiting()).isEmpty();

    storage.finish(uriUpdates);
    assertNoIncompleteTasks(storage);
  }

  @Test
  public void canResetAllEmpty() throws Exception {
    storage.resetAll();
    assertNoIncompleteTasks(storage);
  }

  @Test
  public void canResetAllUpdate() throws Exception {
    storage.create(REF_UPDATE);
    storage.start(uriUpdates);

    storage.resetAll();
    assertThat(storage.listWaiting()).containsExactly(REF_UPDATE);
    assertThat(storage.listRunning()).isEmpty();
  }

  @Test
  public void canCompleteResetAllUpdate() throws Exception {
    storage.create(REF_UPDATE);
    storage.start(uriUpdates);
    storage.resetAll();

    storage.start(uriUpdates);
    assertThat(storage.listRunning()).containsExactly(REF_UPDATE);
    assertThat(storage.listWaiting()).isEmpty();

    storage.finish(uriUpdates);
    assertNoIncompleteTasks(storage);
  }

  @Test
  public void canResetAllMultipleUpdates() throws Exception {
    ReplicateRefUpdate updateB =
        ReplicateRefUpdate.create(
            PROJECT,
            REF,
            getUrish("ssh://example.com/" + PROJECT + ".git"), // uses ssh not http
            REMOTE);
    UriUpdates uriUpdatesB = TestUriUpdates.create(updateB);
    storage.create(REF_UPDATE);
    storage.create(updateB);
    storage.start(uriUpdates);
    storage.start(uriUpdatesB);

    storage.resetAll();
    assertThat(storage.listWaiting()).containsExactly(REF_UPDATE, updateB);
  }

  @Test
  public void canCompleteMultipleResetAllUpdates() throws Exception {
    ReplicateRefUpdate updateB =
        ReplicateRefUpdate.create(
            PROJECT,
            REF,
            getUrish("ssh://example.com/" + PROJECT + ".git"), // uses ssh not http
            REMOTE);
    UriUpdates uriUpdatesB = TestUriUpdates.create(updateB);
    storage.create(REF_UPDATE);
    storage.create(updateB);
    storage.start(uriUpdates);
    storage.start(uriUpdatesB);
    storage.resetAll();

    storage.start(uriUpdates);
    assertThat(storage.listRunning()).containsExactly(REF_UPDATE);
    assertThat(storage.listWaiting()).containsExactly(updateB);

    storage.start(uriUpdatesB);
    assertThat(storage.listRunning()).containsExactly(REF_UPDATE, updateB);
    assertThat(storage.listWaiting()).isEmpty();

    storage.finish(uriUpdates);
    storage.finish(uriUpdatesB);
    assertNoIncompleteTasks(storage);
  }

  @Test(expected = Test.None.class /* no exception expected */)
  public void illegalFinishUncreatedIsGraceful() throws Exception {
    storage.finish(uriUpdates);
  }

  @Test(expected = Test.None.class /* no exception expected */)
  public void illegalDoubleFinishIsGraceful() throws Exception {
    storage.create(REF_UPDATE);
    storage.start(uriUpdates);
    storage.finish(uriUpdates);

    storage.finish(uriUpdates);
  }

  @Test(expected = Test.None.class /* no exception expected */)
  public void illegalDoubleFinishDifferentUriIsGraceful() throws Exception {
    ReplicateRefUpdate updateB =
        ReplicateRefUpdate.create(
            PROJECT,
            REF,
            getUrish("ssh://example.com/" + PROJECT + ".git"), // uses ssh not http
            REMOTE);
    UriUpdates uriUpdatesB = TestUriUpdates.create(updateB);
    storage.create(REF_UPDATE);
    storage.create(updateB);
    storage.start(uriUpdates);
    storage.start(uriUpdatesB);
    storage.finish(uriUpdates);
    storage.finish(uriUpdatesB);

    storage.finish(uriUpdates);
    storage.finish(uriUpdatesB);
    assertThat(storage.listRunning()).isEmpty();
  }

  private void assertNoIncompleteTasks(ReplicationTasksStorage storage) {
    assertThat(storage.listWaiting()).isEmpty();
    assertThat(storage.listRunning()).isEmpty();
  }

  public static URIish getUrish(String uri) {
    try {
      return new URIish(uri);
    } catch (URISyntaxException e) {
      throw new RuntimeException("Cannot instantiate URIish object", e);
    }
  }
}
