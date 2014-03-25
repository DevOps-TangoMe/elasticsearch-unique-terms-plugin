/**
 *  Copyright 2014 TangoMe Inc.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package com.tango.elasticsearch.rest.action.unique;

import static org.elasticsearch.rest.RestRequest.Method.GET;
import static org.elasticsearch.rest.RestRequest.Method.POST;
import static org.elasticsearch.rest.RestStatus.BAD_REQUEST;
import static org.elasticsearch.rest.RestStatus.OK;
import static org.elasticsearch.rest.action.support.RestXContentBuilder.restContentBuilder;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

import net.sf.ehcache.CacheManager;

import org.elasticsearch.action.ActionListener;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.Client;
import org.elasticsearch.common.inject.Inject;
import org.elasticsearch.common.settings.ImmutableSettings;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.util.concurrent.AtomicArray;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.rest.BaseRestHandler;
import org.elasticsearch.rest.RestChannel;
import org.elasticsearch.rest.RestController;
import org.elasticsearch.rest.RestRequest;
import org.elasticsearch.rest.XContentRestResponse;
import org.elasticsearch.rest.XContentThrowableRestResponse;
import org.elasticsearch.rest.action.search.RestSearchAction;
import org.elasticsearch.search.facet.Facet;
import org.elasticsearch.search.facet.Facets;
import org.elasticsearch.search.facet.terms.TermsFacet;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tango.elasticsearch.rest.action.unique.cache.CacheWrapper;
import com.tango.elasticsearch.rest.action.unique.cache.EhcacheWrapper;

public class UniqueTermsAction extends BaseRestHandler {

    public static final DateTimeFormatter ES_INDEX_DATE_FORMAT = DateTimeFormat.forPattern("yyyy.MM.dd-HH").withZone(
            DateTimeZone.forTimeZone(TimeZone.getTimeZone("Etc/UTC")));
    public static final char INDEX_NAME_PREFIX_DELIMITER = '-';
    public static final String TARGET_FACET_NAME = "terms";
    public static final String EHCACHE_CONFIG_PATH_PARAM = "ehcacheConfigPath";
    public static final String EHCACHE_CACHE_NAME_PARAM = "ehcacheCacheName";
    public static final String EHCAHCE_DEFAULT_CACHE_NAME = "searchResponses";

    private CacheWrapper<String, TermsResult> cache;

    protected UniqueTermsAction() {
        super(ImmutableSettings.EMPTY, null);
    }

    @Inject
    public UniqueTermsAction(Settings settings, Client client, RestController controller) {
        super(settings, client);
        String ehcacheConfigPath = componentSettings.get(EHCACHE_CONFIG_PATH_PARAM);
        cache = new EhcacheWrapper<String, TermsResult>(
                componentSettings.get(EHCACHE_CACHE_NAME_PARAM, EHCAHCE_DEFAULT_CACHE_NAME),
                new CacheManager(ehcacheConfigPath));
        controller.registerHandler(GET, "/{index}/_unique", this);
        controller.registerHandler(POST, "/{index}/_unique", this);
    }

    @Override
    public void handleRequest(final RestRequest request, final RestChannel channel) {
        if (logger.isDebugEnabled()) {
            logger.debug("Received unique terms request");
        }
        final Map<SearchRequest, String> searchRequestsToCacheKeyMap = new HashMap<SearchRequest, String>();
        final List<TermsResult> cachedResults = new ArrayList<TermsResult>();
        try {
            prepareRequestsForProcessing(request, searchRequestsToCacheKeyMap, cachedResults);
        } catch (Exception e) {
            if (logger.isDebugEnabled()) {
                logger.debug("failed to parse search request parameters", e);
            }
            try {
                XContentBuilder builder = restContentBuilder(request);
                channel.sendResponse(new XContentRestResponse(request, BAD_REQUEST, builder.startObject().field("error", e.getMessage()).endObject()));
            } catch (IOException e1) {
                logger.error("Failed to send failure response", e1);
            }
            return;
        }
        if (!searchRequestsToCacheKeyMap.isEmpty()) {
            submitSearchRequests(request, channel, searchRequestsToCacheKeyMap, cachedResults);
        } else {
            try {
                aggregateResults(cachedResults, request, channel);
            } catch (IOException ex) {
                processFailure(ex, channel, request);
            }
        }
    }

    private void submitSearchRequests(final RestRequest request, final RestChannel channel, Map<SearchRequest, String> searchRequestsToCacheKeyMap,
            final List<TermsResult> cachedResponses) {
        int indexIndex = 0;
        final AtomicArray<Throwable> searchErrors = new AtomicArray<Throwable>(searchRequestsToCacheKeyMap.size());
        final AtomicInteger counter = new AtomicInteger(searchRequestsToCacheKeyMap.size());
        final AtomicArray<TermsResult> searchResults = new AtomicArray<TermsResult>(searchRequestsToCacheKeyMap.size());
        for (Map.Entry<SearchRequest, String> searchRequestEntry : searchRequestsToCacheKeyMap.entrySet()) {
            final String requestKey = searchRequestEntry.getValue();
            final SearchRequest searchRequest = searchRequestEntry.getKey();
            final int index = indexIndex;
            client.search(searchRequest, new ActionListener<SearchResponse>() {
                @Override
                public void onResponse(SearchResponse response) {
                    try {
                        TermsResult result = extractTermsResult(response);
                        searchResults.set(index, result);
                        if (requestKey.length() > 0 && result != null && result.getOtherCount() == 0) {
                            putToCache(requestKey, result);
                        }
                        if (counter.decrementAndGet() == 0) {
                            Throwable throwable = checkErrors(searchErrors);
                            if (throwable != null) {
                                processFailure(throwable, channel, request);
                            } else {
                                Collections.addAll(cachedResponses, searchResults.toArray(new TermsResult[searchResults.length()]));
                                aggregateResults(cachedResponses, request, channel);
                            }
                        }
                    } catch (Exception e) {
                        if (logger.isDebugEnabled()) {
                            logger.debug("failed to execute search (building response)", e);
                        }
                        onFailure(e);
                    }
                }

                private Throwable checkErrors(AtomicArray<Throwable> searchErrors) {
                    Throwable result = null;
                    for (int i = 0; i < searchErrors.length(); i++) {
                        Throwable th = searchErrors.get(i);
                        if (th != null) {
                            result = th;
                            break;
                        }
                    }
                    return result;
                }

                @Override
                public void onFailure(Throwable e) {
                    searchErrors.set(index, e);
                    if (counter.decrementAndGet() <= 0) {
                        processFailure(e, channel, request);
                    }
                }
            });
            indexIndex++;
        }
    }

    private void processFailure(Throwable e, RestChannel channel, RestRequest request) {
        try {
            channel.sendResponse(new XContentThrowableRestResponse(request, e));
        } catch (IOException e1) {
            logger.error("Failed to send failure response", e1);
        }
    }

    private TermsResult extractTermsResult(SearchResponse searchResponse) {
        TermsResult result = null;
        Facets facets = searchResponse.getFacets();
        if (facets != null) {
            Facet facet = facets.getFacets().get(TARGET_FACET_NAME);
            if (facet != null && facet instanceof TermsFacet) {
                List<String> terms = new ArrayList<String>();
                TermsFacet termsFacet = (TermsFacet) facet;
                List<? extends TermsFacet.Entry> entries = termsFacet.getEntries();
                for (TermsFacet.Entry term : entries) {
                    terms.add(term.getTerm().string());
                }
                result = new TermsResult(terms, termsFacet.getTotalCount(), termsFacet.getMissingCount(), termsFacet.getOtherCount());
            }
        }
        return result;
    }

    private void prepareRequestsForProcessing(RestRequest request,
            Map<SearchRequest, String> searchRequestsToCacheKeyMap,
            List<TermsResult> cachedResponses) throws IOException {
        SearchRequest searchRequest = RestSearchAction.parseSearchRequest(request);
        String[] indices = searchRequest.indices();
        if (searchRequest.source() == null) {
            throw new IllegalArgumentException("Empty request source");
        }
        RequestParamsInfo requestParamsInfo = getRequestInfo(new String(searchRequest.source().copyBytesArray().array()));
        if (request.paramAsBoolean("clearCache", false)) {
            cache.clear();
        }
        searchRequest.listenerThreaded(false);
        for (String index : indices) {
            String cacheKey = "";
            if (requestParamsInfo != null) {
                boolean cachedValueFound = false;
                int datePostfixStart;
                while ((datePostfixStart = index.indexOf(INDEX_NAME_PREFIX_DELIMITER)) >= 0) {
                    String dateStr = index.substring(datePostfixStart + 1);
                    try {
                        DateTime dateTime = ES_INDEX_DATE_FORMAT.parseDateTime(dateStr);
                        long indexStart = dateTime.getMillis();
                        // plus 1 hour
                        long indexEnd = dateTime.plusHours(1).getMillis();
                        // fully covered
                        if (indexStart >= requestParamsInfo.getFromTime() && indexEnd < requestParamsInfo.getToTime()) {
                            // trying to get from cache
                            cacheKey = index + requestParamsInfo.getRequestCacheKey();
                            TermsResult cached = getCachedValue(cacheKey);
                            if (cached != null) {
                                cachedValueFound = true;
                                cachedResponses.add(cached);
                            }
                        }
                        break;
                    } catch (IllegalArgumentException ex) {
                        if (logger.isTraceEnabled()) {
                            logger.trace("Error parsing date from " + dateStr + ": " + ex.getMessage(), ex);
                        }
                    }
                }
                if (cachedValueFound) {
                    continue;
                }
            }
            SearchRequest oneIndexSearchRequest = new SearchRequest(index);
            if (searchRequest.source() != null) {
                oneIndexSearchRequest.source(searchRequest.source().copyBytesArray().array());
            }
            if (searchRequest.extraSource() != null) {
                oneIndexSearchRequest.extraSource(searchRequest.extraSource().copyBytesArray().array());
            }
            oneIndexSearchRequest.searchType(searchRequest.searchType());
            oneIndexSearchRequest.types(searchRequest.types());
            oneIndexSearchRequest.routing(searchRequest.routing());
            oneIndexSearchRequest.preference(searchRequest.preference());
            oneIndexSearchRequest.ignoreIndices(searchRequest.ignoreIndices());
            oneIndexSearchRequest.listenerThreaded(false);
            oneIndexSearchRequest.operationThreading(searchRequest.operationThreading());
            searchRequestsToCacheKeyMap.put(oneIndexSearchRequest, cacheKey);
        }
    }
    private void aggregateResults(List<TermsResult> searchResults, RestRequest request, RestChannel channel)
            throws IOException {
        if (logger.isDebugEnabled()) {
            logger.debug("Unique terms per index results collected, start aggregating");
        }
        UniqueTermsResponse uniqueTermsResponse = aggregateResults(searchResults);
        if (logger.isDebugEnabled()) {
            logger.debug("Unique terms results aggregated");
        }
        XContentBuilder builder = restContentBuilder(request);
        builder.startObject();
        uniqueTermsResponse.toXContent(builder, request);
        builder.endObject();
        channel.sendResponse(new XContentRestResponse(request, OK, builder));
    }

    protected UniqueTermsResponse aggregateResults(Collection<TermsResult> searchResults) {
        int other = 0;
        int total = 0;
        int missing = 0;
        Set<String> uniqueValues = new HashSet<String>();
        if (searchResults != null) {
            for (TermsResult searchResult : searchResults) {
                uniqueValues.addAll(searchResult.getUniqueTerms());
                total += searchResult.getTotalCount();
                missing += searchResult.getMissingCount();
                other += searchResult.getOtherCount();
            }
        }
        return new UniqueTermsResponse(Arrays.asList(
                new UniqueTermsResponse.UniqueTerms(TARGET_FACET_NAME, uniqueValues.size(), total, missing, other)));
    }

    private void putToCache(String key, TermsResult value) {
        if (logger.isDebugEnabled()) {
            logger.debug("Put to cache for key '" + key + "'");
        }
        cache.put(key, value);
    }

    private TermsResult getCachedValue(String key) {
        TermsResult result = cache.get(key);
        if (result != null) {
            if (logger.isDebugEnabled()) {
                logger.debug("Cached value found for key '" + key + "'");
            }
        }
        return result;
    }

    /**
     * Parse date request information and validates that request has expected format
     * <p/>
     * Example of input:
     * <code> {"facets": { "terms": { "terms": { "field": "@fields.uid", "size": 10000000, "order": "count", "exclude":
     * [] }, "facet_filter": { "fquery": { "query": { "filtered": { "query": { "bool": { "should": [ { "query_string": {
     * "query": "*" } } ] } }, "filter": { "bool": { "must": [ { "range": { "@timestamp": { "from": 1395275639569, "to":
     * "now" } } }, { "fquery": { "query": { "query_string": { "query": "@fields.tracer.service.name:(\"Like\")" } },
     * "_cache": true } }, { "terms": { "@fields.tracer.ip.country.name": ["UNITED STATES"] } } ] } } } } } } } },
     * "size": 0} }
     * </code>
     * 
     * @param requestSource source of request
     * @return parsed information
     * @throws IOException
     */
    protected RequestParamsInfo getRequestInfo(String requestSource) throws IOException {
        RequestParamsInfo result = null;
        if (requestSource != null) {
            ObjectMapper objectMapper = new ObjectMapper();
            JsonNode jsonNode = objectMapper.readTree(requestSource);
            JsonNode facets = jsonNode.findValue("facets");
            {
                if (facets.isMissingNode()) {
                    throw new IllegalArgumentException("No facets found in requests");
                }
                Iterator<String> iterator = facets.fieldNames();
                while (iterator.hasNext()) {
                    String facetName = iterator.next();
                    if (!TARGET_FACET_NAME.equals(facetName)) {
                        throw new IllegalArgumentException("Unexpected facet name: " + facetName);
                    }
                }
            }
            JsonNode terms = facets.path(TARGET_FACET_NAME);
            if (terms.isMissingNode()) {
                throw new IllegalArgumentException(String.format("No facet with name '%s' found in requests", TARGET_FACET_NAME));
            }
            JsonNode range = jsonNode.findValue("@timestamp");
            if (range.isMissingNode()) {
                throw new IllegalArgumentException("No @timestamp found in requests");
            }
            String from = range.path("from").asText();
            String to = range.path("to").asText();
            if (from.length() > 0 && to.length() > 0) {
                long currentTime = System.currentTimeMillis();
                long fromLong = "now".equals(from) ? currentTime : Long.parseLong(from);
                long toLong = "now".equals(to) ? currentTime : Long.parseLong(to);
                Iterator<JsonNode> iterator = range.elements();
                while (iterator.hasNext()) {
                    iterator.next();
                    iterator.remove();
                }
                result = new RequestParamsInfo(fromLong, toLong, objectMapper.writeValueAsString(jsonNode));
            }
        }
        return result;
    }

    public static class RequestParamsInfo {
        private long fromTime;
        private long toTime;
        private String requestCacheKey;

        public RequestParamsInfo(long fromLong, long toLong, String requestCacheKey) {
            this.fromTime = fromLong;
            this.toTime = toLong;
            this.requestCacheKey = requestCacheKey;
        }

        public long getFromTime() {
            return fromTime;
        }

        public long getToTime() {
            return toTime;
        }

        public String getRequestCacheKey() {
            return requestCacheKey;
        }

    }
}
