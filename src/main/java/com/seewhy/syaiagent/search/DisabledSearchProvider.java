package com.seewhy.syaiagent.search;

public class DisabledSearchProvider implements SearchProvider {

    @Override
    public String search(String query) {
        return "Live search is disabled by configuration. Do not fabricate web results.";
    }
}
