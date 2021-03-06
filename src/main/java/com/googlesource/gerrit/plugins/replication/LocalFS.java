// Copyright (C) 2018 The Android Open Source Project
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

import static com.googlesource.gerrit.plugins.replication.ReplicationQueue.repLog;

import com.google.gerrit.entities.Project;
import java.io.File;
import java.io.IOException;
import org.eclipse.jgit.internal.storage.file.FileRepository;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.URIish;

public class LocalFS implements AdminApi {

  private final URIish uri;

  public LocalFS(URIish uri) {
    this.uri = uri;
  }

  @Override
  public boolean createProject(Project.NameKey project, String head) {
    try (Repository repo = new FileRepository(uri.getPath())) {
      repo.create(true /* bare */);

      if (head != null && head.startsWith(Constants.R_REFS)) {
        RefUpdate u = repo.updateRef(Constants.HEAD);
        u.disableRefLog();
        u.link(head);
      }
      repLog.atInfo().log("Created local repository: %s", uri);
    } catch (IOException e) {
      repLog.atSevere().withCause(e).log("Error creating local repository %s", uri.getPath());
      return false;
    }
    return true;
  }

  @Override
  public boolean deleteProject(Project.NameKey project) {
    try {
      recursivelyDelete(new File(uri.getPath()));
      repLog.atInfo().log("Deleted local repository: %s", uri);
    } catch (IOException e) {
      repLog.atSevere().withCause(e).log("Error deleting local repository %s:\n", uri.getPath());
      return false;
    }
    return true;
  }

  @Override
  public boolean updateHead(Project.NameKey project, String newHead) {
    try (Repository repo = new FileRepository(uri.getPath())) {
      if (newHead != null) {
        RefUpdate u = repo.updateRef(Constants.HEAD);
        u.link(newHead);
      }
    } catch (IOException e) {
      repLog.atSevere().withCause(e).log(
          "Failed to update HEAD of repository %s to %s", uri.getPath(), newHead);
      return false;
    }
    return true;
  }

  private static void recursivelyDelete(File dir) throws IOException {
    File[] contents = dir.listFiles();
    if (contents != null) {
      for (File d : contents) {
        if (d.isDirectory()) {
          recursivelyDelete(d);
        } else {
          if (!d.delete()) {
            throw new IOException("Failed to delete: " + d.getAbsolutePath());
          }
        }
      }
    }
    if (!dir.delete()) {
      throw new IOException("Failed to delete: " + dir.getAbsolutePath());
    }
  }
}
