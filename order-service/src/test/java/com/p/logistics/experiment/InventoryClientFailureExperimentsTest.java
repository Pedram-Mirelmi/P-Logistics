package com.p.logistics.experiment;

import com.github.tomakehurst.wiremock.http.Fault;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.p.logistics.inventory.InventoryClient;
import com.p.logistics.inventory.InventoryUnavailableException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Experiment E (Step 05's "Required failure experiments").
 * <p>
 * InventoryClient talks to a real HTTP server here - WireMock standing in
 * for inventory-service - so each test below can force exactly one failure
 * mode on demand (a real running inventory-service won't let you dictate
 * "hang for 5 seconds" or "reset the connection mid-response"). No Spring
 * context: this is InventoryClient in isolation, built directly against
 * WireMock's URL, matching how it's really constructed in production
 * ({@code new InventoryClient(inventoryServiceUrl)}).
 * <p>
 * The question the chapter asks for every scenario: <b>can the client know
 * whether the operation executed?</b> Each test's comment answers it.
 * <p>
 * InventoryClient's timeouts (see its source): connect 2000ms, read 3000ms.
 */
class InventoryClientFailureExperimentsTest {

    @RegisterExtension
    static WireMockExtension wireMock = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    private static final String PRODUCT_ID = "FAILURE-EXPERIMENT-PRODUCT";
    private static final String RESERVE_PATH = "/inventory/" + PRODUCT_ID + "/reserve";

    @Test
    void serverUnavailable_clientCanTellTheOperationNeverExecuted() {
        // Nothing is listening on this port at all - the TCP handshake
        // itself fails, before any HTTP request is even framed.
        InventoryClient client = new InventoryClient("http://localhost:1");

        assertThatThrownBy(() -> client.reserve(PRODUCT_ID, 1))
                .isInstanceOf(InventoryUnavailableException.class);

        // Answer: yes. A connection that was never established could not
        // possibly have carried a request the server acted on. This is the
        // one case in this whole file where "didn't execute" is a safe
        // conclusion, not just a hope.
    }

    @Test
    void timeoutWaitingForResponse_requestReachedTheServerButClientCannotKnowTheOutcome() {
        wireMock.stubFor(post(urlEqualTo(RESERVE_PATH))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"reserved\":true}")
                        // Longer than InventoryClient's 3000ms read timeout.
                        .withFixedDelay(5000)));

        InventoryClient client = new InventoryClient(wireMock.baseUrl());

        assertThatThrownBy(() -> client.reserve(PRODUCT_ID, 1))
                .isInstanceOf(InventoryUnavailableException.class);

        // The exception above only tells us the client gave up waiting -
        // not what inventory-service did. Proof the ambiguity is real: the
        // request already arrived and, per the stub, WILL still be
        // "processed" and answered - just to nobody who's listening.
        wireMock.verify(1, postRequestedFor(urlEqualTo(RESERVE_PATH)));

        // Answer: no. From InventoryUnavailableException alone, this case
        // is indistinguishable from serverUnavailable() above, yet here the
        // reservation may well have gone through. Retrying blindly (see
        // retryAfterAmbiguousOutcome... below) is what turns that
        // uncertainty into a real double reservation.
    }

    @Test
    void artificialLatencyUnderTheTimeoutIsJustSlow_notAFailure() {
        wireMock.stubFor(post(urlEqualTo(RESERVE_PATH))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"reserved\":true}")
                        // Under the 3000ms read timeout, so this must still
                        // succeed - the contrast case for the timeout above.
                        .withFixedDelay(1000)));

        InventoryClient client = new InventoryClient(wireMock.baseUrl());

        assertThat(client.reserve(PRODUCT_ID, 1)).isTrue();

        // Answer: yes, trivially - latency alone, below the configured
        // timeout, is not a failure mode. The client got a real answer.
    }

    @Test
    void retryAfterAnAmbiguousOutcomeCanSilentlyDoubleReserve() {
        wireMock.stubFor(post(urlEqualTo(RESERVE_PATH))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"reserved\":true}")));

        InventoryClient client = new InventoryClient(wireMock.baseUrl());

        // Stand-in for "the first call timed out (previous test), so the
        // caller retries, unsure whether the first one already reserved
        // stock." InventoryClient sends no idempotency/request key, and
        // POST /inventory/{id}/reserve is not idempotent server-side
        // (InventoryTransactionService.reserve creates a brand new
        // InventoryReservation row every time it's called) - so this retry
        // is a second, independent reservation, not a safe replay.
        boolean first = client.reserve(PRODUCT_ID, 1);
        boolean second = client.reserve(PRODUCT_ID, 1);

        assertThat(first).isTrue();
        assertThat(second).isTrue();
        wireMock.verify(2, postRequestedFor(urlEqualTo(RESERVE_PATH)));

        // Answer: this is the "retry" and "duplicate request" pair from the
        // required list. The client cannot know the first attempt's true
        // outcome, so it also cannot know whether retrying is safe - and
        // here, concretely, it wasn't: two reservations were made for what
        // was meant to be one order line.
    }

    @Test
    void connectionDroppedMidRequest_looksIdenticalToNeverReachingTheServer() {
        wireMock.stubFor(post(urlEqualTo(RESERVE_PATH))
                .willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));

        InventoryClient client = new InventoryClient(wireMock.baseUrl());

        assertThatThrownBy(() -> client.reserve(PRODUCT_ID, 1))
                .isInstanceOf(InventoryUnavailableException.class);

        // Answer: no, and this is the sharpest version of the question.
        // InventoryClient throws the exact same InventoryUnavailableException
        // here as it did for serverUnavailable() above - but a real
        // "connection reset" can happen AFTER a server already committed a
        // write and only lost the reply on the way out. Same exception
        // type, opposite ground truth, and nothing in the client's own
        // information lets it tell the two apart.
    }

    @Test
    void malformedResponseBodyFailsLoudInsteadOfSilentlyCorrupting() {
        wireMock.stubFor(post(urlEqualTo(RESERVE_PATH))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{ this is not valid json")));

        InventoryClient client = new InventoryClient(wireMock.baseUrl());

        // Whatever exception type surfaces, the important property being
        // checked is that reserve() never silently returns true/false for a
        // 200 response it couldn't actually parse.
        assertThatThrownBy(() -> client.reserve(PRODUCT_ID, 1))
                .isInstanceOf(RuntimeException.class);

        // Answer: yes here, but only because this failure is loud. A
        // malformed body throws instead of being coerced into some default
        // boolean - the dangerous version of this bug is a response that
        // parses but means something different than the server intended,
        // which no amount of exception handling catches.
    }
}
