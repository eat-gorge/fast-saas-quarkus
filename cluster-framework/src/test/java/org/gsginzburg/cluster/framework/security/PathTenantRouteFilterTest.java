/*
 * Copyright 2026 Gary Ginzburg
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gsginzburg.cluster.framework.security;

import io.vertx.core.http.HttpServerRequest;
import io.vertx.ext.web.RoutingContext;
import org.gsginzburg.cluster.framework.config.ClusterConfig;
import org.gsginzburg.shared.util.Base62;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link PathTenantRouteFilter}.
 *
 * The reroute is URI-based, and {@code RoutingContext.normalisedPath()} carries no query string —
 * so a naive rebuild silently drops every query parameter. That failure is invisible end-to-end:
 * the rerouted path is still correct, the resource sees absent (not corrupt) parameters, falls back
 * to its defaults, and answers 200. These tests pin the query string to the rerouted URI so the
 * regression cannot return unnoticed.
 */
class PathTenantRouteFilterTest {

    private static final UUID TENANT = UUID.fromString("16d18e61-49cf-47a3-aecc-20b8a62588a7");

    private PathTenantRouteFilter filter;
    private RoutingContext ctx;
    private HttpServerRequest request;

    @BeforeEach
    void setUp() {
        ClusterConfig.PathTenantInjection injection = mock(ClusterConfig.PathTenantInjection.class);
        when(injection.enabled()).thenReturn(true);

        ClusterConfig config = mock(ClusterConfig.class);
        when(config.pathTenantInjection()).thenReturn(injection);

        request = mock(HttpServerRequest.class);
        ctx = mock(RoutingContext.class);
        when(ctx.request()).thenReturn(request);

        filter = new PathTenantRouteFilter();
        filter.config = config;
    }

    /** Drives the filter for a {@code /c/{base62}} request and returns the URI it rerouted to. */
    private String rerouteFor(String path, String query) {
        when(ctx.normalizedPath()).thenReturn(path);
        when(request.query()).thenReturn(query);

        filter.intercept(ctx);

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(ctx).reroute(captor.capture());
        return captor.getValue();
    }

    private static String encodedTenant() {
        return Base62.encode(TENANT);
    }

    @Test
    void queryStringSurvivesTheReroute() {
        String rerouted = rerouteFor(
                "/c/" + encodedTenant() + "/api/storefront/order/history",
                "since=1970-01-01T00:00:00.000Z");

        assertThat(rerouted)
                .as("query parameters must not be dropped when the /c/ segment is stripped")
                .isEqualTo("/api/storefront/order/history?since=1970-01-01T00:00:00.000Z");
    }

    @Test
    void multipleQueryParametersAreAllPreserved() {
        String rerouted = rerouteFor("/c/" + encodedTenant() + "/api/storefront/review", "page=0&size=99");

        assertThat(rerouted).isEqualTo("/api/storefront/review?page=0&size=99");
    }

    @Test
    void absentQueryStringAddsNoTrailingQuestionMark() {
        String rerouted = rerouteFor("/c/" + encodedTenant() + "/api/storefront/order/board", null);

        assertThat(rerouted).isEqualTo("/api/storefront/order/board");
    }

    @Test
    void emptyQueryStringAddsNoTrailingQuestionMark() {
        String rerouted = rerouteFor("/c/" + encodedTenant() + "/api/storefront/order/board", "");

        assertThat(rerouted).isEqualTo("/api/storefront/order/board");
    }

    @Test
    void prefixBeforeTenantSegmentIsPreservedAlongWithQuery() {
        String rerouted = rerouteFor("/gorge/c/" + encodedTenant() + "/api/order/history", "since=2026-01-01T00:00:00Z");

        assertThat(rerouted).isEqualTo("/gorge/api/order/history?since=2026-01-01T00:00:00Z");
    }

    @Test
    void decodedTenantIsStoredOnTheRoutingContext() {
        rerouteFor("/c/" + encodedTenant() + "/api/storefront/order/history", "since=2026-01-01T00:00:00Z");

        verify(ctx).put(PathTenantRouteFilter.ATTRIBUTE, TENANT.toString());
    }

    @Test
    void nonTenantPathPassesThroughUntouched() {
        when(ctx.normalizedPath()).thenReturn("/api/storefront/order/history");

        filter.intercept(ctx);

        verify(ctx).next();
        verify(ctx, never()).reroute(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void percentEncodedQueryValueIsPassedThroughVerbatim() {
        // request.query() is the raw, still-encoded query string; the filter must not decode
        // or re-encode it, or an ISO instant's ':' separators would arrive corrupted.
        String rerouted = rerouteFor(
                "/c/" + encodedTenant() + "/api/storefront/order/history",
                "since=2026-01-01T00%3A00%3A00Z&q=spicy%20tacos");

        assertThat(rerouted)
                .isEqualTo("/api/storefront/order/history?since=2026-01-01T00%3A00%3A00Z&q=spicy%20tacos");
    }
}
