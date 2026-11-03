import com.getflowsdk.demo.coingecko.ApiException;
import com.getflowsdk.demo.coingecko.CoinGeckoDemoApiClient;
import com.getflowsdk.demo.coingecko.api.DefaultApi;
import com.getflowsdk.demo.coingecko.model.CoinsMarketsItem;
import java.util.List;

/**
 * Lists the five largest coins by market cap, then shows how an API error surfaces.
 *
 * <p>Set {@code COINGECKO_DEMO_API_KEY} to your free CoinGecko Demo key. Without it, requests go out
 * unauthenticated, which CoinGecko's public endpoint accepts at a lower rate limit.
 */
public final class QuickStart {

    public static void main(String[] args) {
        String key = System.getenv("COINGECKO_DEMO_API_KEY");
        CoinGeckoDemoApiClient.Builder builder = CoinGeckoDemoApiClient.builder();
        if (key == null || key.isBlank()) {
            builder.authentication("headerAuth", target -> { });
        } else {
            builder.headerAuth(key);
        }
        DefaultApi api = builder.build().defaultApi();

        try {
            System.out.println("Ping: " + api.pingServer().geckoSays());

            List<CoinsMarketsItem> coins = api.coinsMarkets("usd");
            for (CoinsMarketsItem coin : coins.subList(0, Math.min(5, coins.size()))) {
                System.out.printf("#%d %-10s %14s USD  %+.2f%% (24h)%n", coin.marketCapRank(), coin.name(),
                        coin.currentPrice().toPlainString(), coin.priceChangePercentage24h());
            }
        } catch (ApiException e) {
            System.err.println("Request failed with HTTP " + e.statusCode() + ": " + e.getMessage());
            System.exit(1);
        }

        try {
            api.coinsId("no-such-coin");
        } catch (ApiException e) {
            System.out.println("Unknown coin -> ApiException, HTTP " + e.statusCode());
        }
    }
}
