package com.getflowsdk.demo.coingecko;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.getflowsdk.demo.coingecko.api.DefaultApi;
import com.getflowsdk.demo.coingecko.model.CoinsMarketsItem;
import com.getflowsdk.demo.coingecko.model.CoinsMarketsOrder;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Tests the generated client against a local fake server. They never touch the network. */
class DefaultApiTest {

    private static final String MARKETS_JSON = """
            [
              {
                "id": "bitcoin", "symbol": "btc", "name": "Bitcoin",
                "image": "https://example.test/bitcoin.png",
                "current_price": 67123.45, "market_cap": 1320000000000, "market_cap_rank": 1,
                "price_change_percentage_24h": -1.25,
                "ath_date": "2025-10-06T18:57:42.558Z",
                "roi": null,
                "last_updated": "2026-11-03T09:30:00.000Z",
                "a_field_added_later": {"ignored": true}
              },
              {
                "id": "ethereum", "symbol": "eth", "name": "Ethereum",
                "image": "https://example.test/ethereum.png",
                "current_price": 3201.9, "market_cap": 386000000000, "market_cap_rank": 2,
                "price_change_percentage_24h": 0.5,
                "last_updated": "2026-11-03T09:30:00.000Z"
              }
            ]
            """;

    private FakeServer server;

    @BeforeEach
    void startServer() throws Exception {
        server = new FakeServer();
    }

    @AfterEach
    void stopServer() {
        server.close();
    }

    private CoinGeckoDemoApiClient.Builder builder() {
        return CoinGeckoDemoApiClient.builder().baseUrl(server.baseUrl());
    }

    private DefaultApi api() {
        return builder().authentication("headerAuth", target -> { }).build().defaultApi();
    }

    @Test
    void readsRealisticJsonIntoTypedObjects() {
        server.respond(200, MARKETS_JSON);

        List<CoinsMarketsItem> coins = api().coinsMarkets("usd");

        assertEquals(2, coins.size());
        CoinsMarketsItem bitcoin = coins.get(0);
        assertEquals("bitcoin", bitcoin.id());
        assertEquals("Bitcoin", bitcoin.name());
        assertEquals(0, new BigDecimal("67123.45").compareTo(bitcoin.currentPrice()));
        assertEquals(1L, bitcoin.marketCapRank());
        assertEquals(0, new BigDecimal("-1.25").compareTo(bitcoin.priceChangePercentage24h()));
        assertEquals(OffsetDateTime.parse("2025-10-06T18:57:42.558Z"), bitcoin.athDate());
        assertNull(bitcoin.roi());
        assertEquals("ethereum", coins.get(1).id());
        assertEquals("GET", server.last().method());
        assertEquals("/api/v3/coins/markets", server.last().path());
    }

    @Test
    void exposesStatusAndHeadersWithHttpInfo() {
        server.respond(200, "{\"gecko_says\":\"(V3) To the Moon!\"}");

        ApiResponse<?> response = api().pingServerWithHttpInfo();

        assertEquals(200, response.statusCode());
        assertEquals("application/json", response.headers().firstValue("content-type").orElseThrow());
        assertEquals("/api/v3/ping", server.last().path());
    }

    @Test
    void turnsAnErrorStatusIntoApiExceptionWithTheHttpCode() {
        server.respond(404, "{\"error\":\"coin not found\"}");

        ApiException error = assertThrows(ApiException.class, () -> api().coinsId("no-such-coin"));

        assertEquals(404, error.statusCode());
        assertTrue(error.isClientError());
        assertFalse(error.isServerError());
        assertTrue(error.responseBody().contains("coin not found"));
    }

    @Test
    void turnsAServerErrorIntoApiException() {
        server.respond(503, "{\"error\":\"try later\"}");

        ApiException error = assertThrows(ApiException.class, () -> api().pingServer());

        assertEquals(503, error.statusCode());
        assertTrue(error.isServerError());
    }

    @Test
    void sendsTheApiKeyInTheDocumentedHeader() {
        server.respond(200, "{\"gecko_says\":\"ok\"}");

        builder().headerAuth("secret-demo-key").build().defaultApi().pingServer();

        assertEquals("secret-demo-key", server.last().header("x-cg-demo-api-key"));
        assertFalse(server.last().query().containsKey("x_cg_demo_api_key"));
    }

    @Test
    void sendsTheApiKeyInTheQueryWhenThatSchemeIsConfigured() {
        server.respond(200, "{\"gecko_says\":\"ok\"}");

        builder().queryAuth("query-key").build().defaultApi().pingServer();

        assertEquals("query-key", server.last().query().get("x_cg_demo_api_key"));
    }

    @Test
    void sendsNoKeyWhenTheSchemeIsEmpty() {
        server.respond(200, "{\"gecko_says\":\"ok\"}");

        api().pingServer();

        assertNull(server.last().header("x-cg-demo-api-key"));
        assertEquals(Map.of(), server.last().query());
    }

    @Test
    void encodesQueryParametersSafely() {
        server.respond(200, "[]");

        api().coinsMarkets("usd", "bitcoin,ethereum", "Tom & Jerry", "a=b#c", null, null,
                CoinsMarketsOrder.VOLUME_DESC, 50L, 2L, true, null, null, null, null);

        FakeServer.Received request = server.last();
        String raw = request.rawQuery();
        assertFalse(raw.contains(" "), "no raw space in " + raw);
        assertFalse(raw.contains("#"), "no raw fragment mark in " + raw);
        assertTrue(raw.contains("names=Tom%20%26%20Jerry") || raw.contains("names=Tom+%26+Jerry"),
                "ampersand and spaces must be escaped in " + raw);
        Map<String, String> query = request.query();
        assertEquals("usd", query.get("vs_currency"));
        assertEquals("bitcoin,ethereum", query.get("ids"));
        assertEquals("Tom & Jerry", query.get("names"));
        assertEquals("a=b#c", query.get("symbols"));
        assertEquals("volume_desc", query.get("order"));
        assertEquals("50", query.get("per_page"));
        assertEquals("2", query.get("page"));
        assertEquals("true", query.get("sparkline"));
        assertFalse(query.containsKey("category"), "null optional parameters are omitted");
    }

    @Test
    void escapesPathParameters() {
        server.respond(404, "{}");

        assertThrows(ApiException.class, () -> api().coinsId("a/b c"));

        assertEquals("/api/v3/coins/a%2Fb%20c", server.last().rawPath());
    }

    @Test
    void anEnumValueAddedLaterBecomesUnknown() throws Exception {
        var mapper = JsonSupport.newObjectMapper();

        assertSame(CoinsMarketsOrder.UNKNOWN, mapper.readValue("\"brand_new_order\"", CoinsMarketsOrder.class));
        assertSame(CoinsMarketsOrder.VOLUME_DESC, mapper.readValue("\"volume_desc\"", CoinsMarketsOrder.class));
        assertNull(CoinsMarketsOrder.UNKNOWN.value());
    }
}
