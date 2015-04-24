// Copyright (C) 2015 The Android Open Source Project
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

import com.google.gerrit.common.data.GlobalCapability;
import com.google.gerrit.extensions.annotations.RequiresCapability;
import com.google.gerrit.sshd.CommandMetaData;
import com.google.gerrit.sshd.SshCommand;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.google.common.base.Strings;
import com.google.inject.Inject;

import com.googlesource.gerrit.plugins.replication.ReplicationConfig.FilterType;

import org.kohsuke.args4j.Option;

import java.util.List;

@RequiresCapability(GlobalCapability.ADMINISTRATE_SERVER)
@CommandMetaData(name = "list", description = "List specific remote destinations information")
final class ListCommand extends SshCommand {
  @Option(name = "--remote", metaVar = "PATTERN", usage = "pattern to match remote name on")
  private String remote;

  @Option(name = "--detail", usage = "print remote destination detail information")
  private boolean detail;

  @Option(name = "--json", usage = "output in json format")
  private boolean json;

  @Inject
  private ReplicationConfig config;

  @Override
  protected void run() {
    List<Destination> dest = config.getDestinations(FilterType.ALL);
    for (Destination d : dest) {
      if (matches(d.getRemoteConfig().getName())) {
        printRemote(d, detail);
      }
    }
  }

  private boolean matches(String name) {
    return (Strings.isNullOrEmpty(remote)
        || name.contains(remote)
        || name.matches(remote));
  }

  private void addProperty(JsonObject obj, String key, String[] values) {
    if (values.length > 0) {
      JsonArray list = new JsonArray();
      for (String v : values) {
        list.add(new JsonPrimitive(v));
      }
      obj.add(key, list);
    }
  }

  private void printRemote(Destination d, boolean detail) {
    if (json) {
      JsonObject obj = new JsonObject();
      obj.addProperty("remote", d.getRemoteConfig().getName());
      addProperty(obj, "Url", d.getUrls());
      if (detail) {
        addProperty(obj, "AdminUrl", d.getAdminUrls());
        addProperty(obj, "AuthGroup", d.getAuthGroupNames());
        addProperty(obj, "Project", d.getProjects());
      }
      stdout.print(obj.toString() + "\n");
    } else {
      StringBuilder out = new StringBuilder();
      out.append("Remote: ")
        .append(d.getRemoteConfig().getName())
        .append("\n");
      for (String url : d.getUrls()) {
        out.append("Url: ")
          .append(url)
          .append("\n");
      }

      if (detail) {
        for (String adminUrl : d.getAdminUrls()) {
          out.append("AdminUrl: ")
            .append(adminUrl)
            .append("\n");
        }

        for (String authGroup : d.getAuthGroupNames()) {
          out.append("AuthGroup: ")
            .append(authGroup)
            .append("\n");
        }

        for (String project : d.getProjects()) {
          out.append("Project: ")
            .append(project)
            .append("\n");
        }
      }
      stdout.print(out.toString() + "\n");
    }
  }
}