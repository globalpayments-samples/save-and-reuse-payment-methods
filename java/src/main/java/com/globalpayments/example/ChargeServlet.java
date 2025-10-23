package com.globalpayments.example;

import com.google.gson.Gson;
import io.github.cdimascio.dotenv.Dotenv;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Charge Endpoint
 * 
 * POST /charge - Process immediate payment ($25.00)
 */
@WebServlet(name = "ChargeServlet", urlPatterns = {"/charge"})
public class ChargeServlet extends HttpServlet {
    
    private static final Gson gson = new Gson();
    private final Dotenv dotenv = Dotenv.configure().ignoreIfMissing().load();
    
    @Override
    public void init() throws ServletException {
        try {
            PaymentUtils.configureSdk();
        } catch (Exception e) {
            throw new ServletException("Failed to configure Global Payments SDK", e);
        }
    }
    
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        
        handleCORS(response);
        
        try {
            // Parse JSON input
            String jsonString = request.getReader().lines().collect(Collectors.joining());
            @SuppressWarnings("unchecked")
            Map<String, Object> data = gson.fromJson(jsonString, Map.class);

            System.out.println("💳 CHARGE REQUEST RECEIVED:");
            System.out.println("   ⏰ Timestamp: " + LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            System.out.println("   🔍 Request Data: " + gson.toJson(data));

            if (data == null || isEmpty((String) data.get("paymentMethodId"))) {
                System.err.println("❌ VALIDATION ERROR: Missing paymentMethodId");
                sendErrorResponse(response, 400, "Payment method ID is required", "VALIDATION_ERROR");
                return;
            }

            String paymentMethodId = (String) data.get("paymentMethodId");
            System.out.println("   🆔 Payment Method ID: " + paymentMethodId);

            Map<String, Object> paymentMethod = JsonStorage.findPaymentMethod(paymentMethodId);
            if (paymentMethod == null) {
                System.err.println("❌ PAYMENT METHOD NOT FOUND: " + paymentMethodId);
                sendErrorResponse(response, 404, "Payment method not found", "NOT_FOUND");
                return;
            }

            System.out.println("   💳 Card: " + paymentMethod.get("cardBrand") + " ending in " + paymentMethod.get("last4"));
            System.out.println("   🔐 Stored Payment Token: " + ((String) paymentMethod.get("storedPaymentToken")).substring(0, Math.min(8, ((String) paymentMethod.get("storedPaymentToken")).length())) + "...");

            BigDecimal amount = new BigDecimal("25.00");
            String currency = "USD";
            System.out.println("   💵 Amount: $" + amount + " " + currency);
            
            Map<String, Object> transactionResult = null;
            boolean mockMode = false;

            // Check if mock mode is enabled globally
            if (MockModeServlet.isMockModeEnabled()) {
                mockMode = true;
                String last4 = (String) paymentMethod.get("last4");

                System.out.println("🟡 MOCK MODE - Generating mock payment response");
                System.out.println("   🎭 Card ending in: " + last4);
                transactionResult = MockResponses.getPaymentResponse(amount, paymentMethodId);
                System.out.println("✅ MOCK PAYMENT COMPLETE");
            } else {
                // Live mode - no fallback to mock
                String appKey = dotenv.get("GP_API_APP_KEY");
                if (appKey != null && !appKey.trim().isEmpty()) {
                    try {
                        String storedPaymentToken = (String) paymentMethod.get("storedPaymentToken");
                        System.out.println("🟢 LIVE MODE - Processing payment via GP API...");
                        transactionResult = PaymentUtils.processPaymentWithSDK(storedPaymentToken, amount, currency);
                        System.out.println("✅ LIVE PAYMENT COMPLETE");
                    } catch (Exception e) {
                        System.err.println("❌ LIVE MODE - Payment processing failed:");
                        System.err.println("   Error: " + e.getMessage());
                        e.printStackTrace();
                        sendErrorResponse(response, 422, "Payment failed: " + e.getMessage(), "PAYMENT_ERROR");
                        return;
                    }
                } else {
                    System.err.println("❌ CONFIGURATION ERROR - No GP_API_APP_KEY found in environment");
                    sendErrorResponse(response, 503, "Payment service not configured", "CONFIGURATION_ERROR");
                    return;
                }
            }
            
            // Build response
            Map<String, Object> responseData = new HashMap<>(transactionResult);
            
            Map<String, Object> paymentMethodInfo = new HashMap<>();
            paymentMethodInfo.put("id", paymentMethod.get("id"));
            paymentMethodInfo.put("type", "card");
            paymentMethodInfo.put("brand", paymentMethod.get("cardBrand"));
            paymentMethodInfo.put("last4", paymentMethod.get("last4"));
            paymentMethodInfo.put("nickname", paymentMethod.get("nickname") != null ? paymentMethod.get("nickname") : "");
            
            responseData.put("paymentMethod", paymentMethodInfo);
            responseData.put("mockMode", mockMode);
            
            Map<String, Object> finalResponse = new HashMap<>();
            finalResponse.put("success", true);
            finalResponse.put("data", responseData);
            finalResponse.put("message", "Payment processed successfully");
            finalResponse.put("timestamp", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            
            response.getWriter().write(gson.toJson(finalResponse));
            
        } catch (Exception e) {
            e.printStackTrace();
            sendErrorResponse(response, 500, "Payment processing failed", "SERVER_ERROR");
        }
    }
    
    @Override
    protected void doOptions(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        handleCORS(response);
        response.setStatus(HttpServletResponse.SC_OK);
    }
    
    private void handleCORS(HttpServletResponse response) {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Access-Control-Allow-Origin", "*");
        response.setHeader("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        response.setHeader("Access-Control-Allow-Headers", "Content-Type, Authorization");
    }
    
    private void sendErrorResponse(HttpServletResponse response, int statusCode, String message, String errorCode) 
            throws IOException {
        response.setStatus(statusCode);
        
        Map<String, Object> errorResponse = new HashMap<>();
        errorResponse.put("success", false);
        errorResponse.put("message", message);
        errorResponse.put("timestamp", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        
        if (errorCode != null) {
            errorResponse.put("error_code", errorCode);
        }
        
        response.getWriter().write(gson.toJson(errorResponse));
    }
    
    private boolean isEmpty(String str) {
        return str == null || str.trim().isEmpty();
    }
}