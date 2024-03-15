package com.boku.demo;

import jakarta.servlet.AsyncContext;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

@WebServlet(urlPatterns = "/", asyncSupported = true)
public class Servlet extends HttpServlet {
    private static final AtomicLong sum = new AtomicLong(0);
    private static CountDownLatch latch = new CountDownLatch(1);

    @Override
    public void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String body = req.getReader().lines().collect(Collectors.joining(System.lineSeparator()));
        final AsyncContext asyncContext = req.startAsync(req, resp);
        asyncContext.setTimeout(300000); // request will wait for response for 5 minutes before throwing timeout error
        final PrintWriter responseWriter = asyncContext.getResponse().getWriter();

        if (body.equals("end")) {
            handleEndRequest(asyncContext, responseWriter);
        } else {
            handleNumberRequest(asyncContext, responseWriter, body);
        }
    }

    @Override
    public void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.getWriter().print("App is working :)\n");
    }

    private void handleEndRequest(AsyncContext asyncContext, PrintWriter responseWriter) {
        new Thread(() -> {
            try {
                Thread.sleep(50); // We need to make sure that the end request is processed synchronously after all number requests
                responseWriter.print(sum.get() + "\n");
                responseWriter.flush();
                asyncContext.complete();
                latch.countDown(); // Let all requests print the sum response
                Thread.sleep(50); // Wait until all requests print the sum response before resetting the sum
            } catch (InterruptedException e) {
                System.out.println(e.getMessage());
            } finally {
                sum.set(0);
                latch = new CountDownLatch(1);
            }
        }).start();
    }

    private void handleNumberRequest(AsyncContext asyncContext, PrintWriter responseWriter, String body) {
        new Thread(() -> {
            try {
                long number = Long.parseLong(body);
                sum.addAndGet(number);
                latch.await(); // Wait till the end response to get the final sum
                responseWriter.print(sum.get() + "\n");
            } catch (Exception e) {
                System.out.println(e.getMessage());
                responseWriter.print("Invalid number!\n");
            } finally {
                responseWriter.flush();
                asyncContext.complete();
            }
        }).start();
    }
}