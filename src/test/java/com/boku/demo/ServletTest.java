package com.boku.demo;

import jakarta.servlet.AsyncContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringReader;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import static org.mockito.ArgumentMatchers.any;

public class ServletTest {
    final int numOfThreads = 20;
    final Servlet servlet = new Servlet();

    ExecutorService executorService;

    @BeforeEach
    public void setUp() {
        executorService = Executors.newFixedThreadPool(numOfThreads);
    }

    @AfterEach
    public void destroy() throws InterruptedException {
        executorService.shutdown();
        executorService.awaitTermination(1, TimeUnit.MINUTES);
    }

    // the sum of all numbers should be printed
    @Test
    public void testParallelRequestWithEndRequestAtTheEnd() {
        sendParallelRequests(executorService, numOfThreads, false);
    }

    // the sum of all numbers should be printed regardless of the order of the "end" request, because requests are sent in parallel
    @Test
    public void testParallelRequestWithEndRequestInTheMiddle() {
        sendParallelRequests(executorService, 9, true);
    }

    // zero should be printed
    @Test
    public void testOneEndRequest() throws IOException, InterruptedException {
        sendPostRequest("end", "0");
    }

    private void sendParallelRequests(ExecutorService executorService, int numRequests, boolean endRequestInTheMiddle) {
        int expectedSum = IntStream.range(1, numRequests).sum() + (endRequestInTheMiddle ? numRequests - numRequests / 2 : 0);

        CountDownLatch latch = new CountDownLatch(numRequests);

        for (int i = 1; i < numRequests + 1; i++) {
            int finalI = i;
            executorService.execute(() -> {
                try {
                    boolean condition = endRequestInTheMiddle ? finalI == numRequests / 2 : finalI == numRequests;
                    String parameter = condition ? "end" : Integer.toString(finalI);
                    sendPostRequest(parameter, Integer.toString(expectedSum));
                } catch (IOException | InterruptedException e) {
                    System.out.println(e.getMessage());
                } finally {
                    latch.countDown();
                }
            });
        }

        try {
            latch.await();
        } catch (InterruptedException e) {
            System.out.println(e.getMessage());
        }
    }

    private void sendPostRequest(String number, String sum) throws IOException, InterruptedException {
        HttpServletRequest request = Mockito.mock(HttpServletRequest.class);
        BufferedReader reader = new BufferedReader(new StringReader(number));
        Mockito.when(request.getReader()).thenReturn(reader);

        HttpServletResponse response = Mockito.mock(HttpServletResponse.class);
        PrintWriter writer = Mockito.mock(PrintWriter.class);
        Mockito.when(response.getWriter()).thenReturn(writer);

        AsyncContext asyncContext = Mockito.mock(AsyncContext.class);
        Mockito.when(asyncContext.getResponse()).thenReturn(response);
        Mockito.when(request.startAsync(any(), any())).thenReturn(asyncContext);

        servlet.doPost(request, response);

        CountDownLatch latch = new CountDownLatch(1);
        latch.await(100, TimeUnit.MILLISECONDS); // Need to wait for the end request to "wake up" and open the latch

        writer.flush();

        Mockito.verify(asyncContext).complete();
        Mockito.verify(response.getWriter()).print(sum + "\n");

        latch.await(100, TimeUnit.MILLISECONDS);  // Need to wait for the end request to "wake up" and set sum back to 0
    }
}
