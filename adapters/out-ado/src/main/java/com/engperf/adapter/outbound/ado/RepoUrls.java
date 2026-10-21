package com.engperf.adapter.outbound.ado;

import com.engperf.domain.structure.Repository;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * The two addresses of one repository: the API base the sync reads from, and the web base a person
 * opens.
 *
 * <p>They travel together because every reading needs both — fetch from one, link to the other —
 * and because the web one has to be built: the pull request list endpoint carries no link, so a
 * reading that only knew the API base produced events nobody could open.
 */
record RepoUrls(String api, String web) {

  static RepoUrls of(Repository repo) {
    String org = AdoEventSource.normOrg(repo.organization());
    String project = enc(repo.project());
    String name = enc(repo.key());
    return new RepoUrls(
        org + "/" + project + "/_apis/git/repositories/" + name,
        org + "/" + project + "/_git/" + name);
  }

  private static String enc(String s) {
    return URLEncoder.encode(s, StandardCharsets.UTF_8).replace("+", "%20");
  }
}
