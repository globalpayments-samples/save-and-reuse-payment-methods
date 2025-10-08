package com.globalpayments.example;

import com.google.gson.Gson;
import io.github.cdimascio.dotenv.Dotenv;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Payment Methods Endpoint
 * 
 * GET /payment-methods - Retrieve saved payment methods
 * POST /payment-methods - Create new payment method (vault token) OR edit existing payment method
 *                         - Create: Requires vaultToken (+ optional nickname, isDefault)
 *                         - Edit: Requires id (+ optional nickname, isDefault) - only nickname and default status can be edited
 */
@WebServlet(name = "PaymentMethodsServlet", urlPatterns = {"/payment-methods"})
public class PaymentMethodsServlet extends HttpServlet {
    
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
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        
        handleCORS(response);
        
        try {
            List<Map<String, Object>> paymentMethods = JsonStorage.getFormattedPaymentMethods();
            
            Map<String, Object> responseData = new HashMap<>();
            responseData.put("success", true);
            responseData.put("data", paymentMethods);
            responseData.put("message", "Payment methods retrieved successfully");
            responseData.put("timestamp", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            
            response.getWriter().write(gson.toJson(responseData));
            
        } catch (Exception e) {
            sendErrorResponse(response, 500, "Failed to retrieve payment methods", "SERVER_ERROR");
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

            // Log incoming request for debugging
            System.out.println("📥 PAYMENT METHOD REQUEST RECEIVED:");
            System.out.println("   ⏰ Timestamp: " + LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            System.out.println("   📄 Raw JSON Length: " + jsonString.length() + " chars");
            System.out.println("   🔍 Parsed Keys: " + (data != null ? data.keySet() : "null"));
            if (data != null) {
                System.out.println("   🔑 paymentToken (camelCase): " + (data.get("paymentToken") != null ? "present" : "missing"));
                System.out.println("   🔑 payment_token (snake_case): " + (data.get("payment_token") != null ? "present" : "missing"));
                System.out.println("   🔑 vaultToken: " + (data.get("vaultToken") != null ? "present" : "missing"));
                System.out.println("   🔑 id: " + (data.get("id") != null ? "present" : "missing"));
                System.out.println("   🔑 customerData: " + (data.get("customerData") != null ? "present" : "missing"));
                System.out.println("   🔑 cardDetails: " + (data.get("cardDetails") != null ? "present" : "missing"));
            }

            // Check if this is an edit operation
            if (data != null && data.get("id") != null) {
                System.out.println("✏️  EDIT OPERATION DETECTED - Routing to handleEditPaymentMethod");
                handleEditPaymentMethod(response, data);
                return;
            }

            // Check if this is a multi-use token creation with customer data
            String paymentToken = (String) data.get("paymentToken");
            String vaultToken = (String) data.get("vaultToken");

            // Validate required fields - either paymentToken + customerData for multi-use, or vaultToken for existing
            if (paymentToken == null && vaultToken == null) {
                System.err.println("❌ VALIDATION ERROR:");
                System.err.println("   Both paymentToken and vaultToken are missing");
                System.err.println("   Available keys: " + (data != null ? data.keySet() : "null"));
                sendErrorResponse(response, 400, "Missing required payment token or vault token", "VALIDATION_ERROR");
                return;
            }

            String nickname = (String) data.get("nickname");
            Boolean isDefault = (Boolean) data.get("isDefault");
            
            boolean mockMode = false;
            Map<String, String> cardDetails = null;
            String finalToken = vaultToken;

            // Handle multi-use token creation with customer data
            if (paymentToken != null) {
                System.out.println("🔄 MULTI-USE TOKEN CREATION FLOW:");
                System.out.println("   📍 Payment Token: " + paymentToken.substring(0, Math.min(12, paymentToken.length())) + "...");

                @SuppressWarnings("unchecked")
                Map<String, String> customerDataMap = (Map<String, String>) data.get("customerData");
                @SuppressWarnings("unchecked")
                Map<String, String> cardDetailsMap = (Map<String, String>) data.get("cardDetails");

                if (customerDataMap == null || cardDetailsMap == null) {
                    System.err.println("❌ VALIDATION ERROR:");
                    System.err.println("   customerData present: " + (customerDataMap != null));
                    System.err.println("   cardDetails present: " + (cardDetailsMap != null));
                    sendErrorResponse(response, 400, "Customer data and card details required for multi-use token creation", "VALIDATION_ERROR");
                    return;
                }

                System.out.println("   👤 Customer: " + customerDataMap.get("first_name") + " " + customerDataMap.get("last_name"));
                System.out.println("   💳 Card Type: " + cardDetailsMap.get("cardType"));
                System.out.println("   🔢 Last 4: " + cardDetailsMap.get("cardLast4"));

                PaymentUtils.CustomerData customerData = new PaymentUtils.CustomerData(customerDataMap);
                PaymentUtils.CardDetails cardDetailObj = new PaymentUtils.CardDetails(cardDetailsMap);

                // Create multi-use token with customer data or use mock
                if (MockModeServlet.isMockModeEnabled()) {
                    mockMode = true;
                    cardDetails = MockResponses.getCardDetailsFromToken(paymentToken);
                    finalToken = paymentToken; // In mock mode, use original token
                    System.out.println("🟡 MOCK MODE - Using payment token as final vault token");
                    System.out.println("   🎭 Mock Card: " + cardDetails.get("brand") + " ending in " + cardDetails.get("last4"));
                } else {
                    String appKey = dotenv.get("GP_API_APP_KEY");
                    if (appKey != null && !appKey.trim().isEmpty()) {
                        try {
                            System.out.println("🟢 LIVE MODE - Creating multi-use token via GP API...");
                            PaymentUtils.MultiUseTokenResult multiUseResult = PaymentUtils.createMultiUseTokenWithCustomer(paymentToken, customerData, cardDetailObj);
                            finalToken = multiUseResult.multiUseToken;

                            // Create cardDetails map from result
                            cardDetails = new HashMap<>();
                            cardDetails.put("brand", multiUseResult.brand);
                            cardDetails.put("last4", multiUseResult.last4);
                            cardDetails.put("expiryMonth", multiUseResult.expiryMonth);
                            cardDetails.put("expiryYear", multiUseResult.expiryYear);
                            cardDetails.put("token", finalToken);

                            System.out.println("✅ LIVE MODE - Multi-use token created successfully");
                            System.out.println("   💳 Card: " + cardDetails.get("brand") + " ending in " + cardDetails.get("last4"));
                            System.out.println("   🔐 Final Token: " + finalToken.substring(0, Math.min(8, finalToken.length())) + "...");
                        } catch (Exception e) {
                            System.err.println("❌ LIVE MODE - Multi-use token creation failed:");
                            System.err.println("   Error: " + e.getMessage());
                            e.printStackTrace();
                            // Fall back to mock mode
                            mockMode = true;
                            cardDetails = MockResponses.getCardDetailsFromToken(paymentToken);
                            finalToken = paymentToken;
                            System.out.println("🟡 FALLBACK - Switching to mock mode due to token creation failure");
                        }
                    } else {
                        System.err.println("❌ CONFIGURATION ERROR - No GP_API_APP_KEY found in environment");
                        sendErrorResponse(response, 503, "Payment service not configured", "CONFIGURATION_ERROR");
                        return;
                    }
                }
            } else {
                // Handle existing vault token (legacy flow)
                System.out.println("🔄 VAULT TOKEN LOOKUP FLOW:");
                System.out.println("   📍 Vault Token: " + vaultToken.substring(0, Math.min(12, vaultToken.length())) + "...");
                finalToken = vaultToken;

                // Check if mock mode is enabled globally
                if (MockModeServlet.isMockModeEnabled()) {
                    mockMode = true;
                    cardDetails = MockResponses.getCardDetailsFromToken(vaultToken);
                    System.out.println("🟡 MOCK MODE - Retrieved mock card details");
                    System.out.println("   🎭 Mock Card: " + cardDetails.get("brand") + " ending in " + cardDetails.get("last4"));
                } else {
                    // Try to get card details from real vault token
                    String appKey = dotenv.get("GP_API_APP_KEY");
                    if (appKey != null && !appKey.trim().isEmpty()) {
                        try {
                            System.out.println("🟢 LIVE MODE - Looking up vault token via GP API...");
                            cardDetails = PaymentUtils.getCardDetailsFromToken(vaultToken);
                            System.out.println("✅ LIVE MODE - Token lookup successful");
                            System.out.println("   💳 Card: " + cardDetails.get("brand") + " ending in " + cardDetails.get("last4"));
                        } catch (Exception e) {
                            System.err.println("❌ LIVE MODE - Token lookup failed:");
                            System.err.println("   Error: " + e.getMessage());
                            e.printStackTrace();
                            // Fall back to mock mode
                            mockMode = true;
                            cardDetails = MockResponses.getCardDetailsFromToken(vaultToken);
                            System.out.println("🟡 FALLBACK - Switching to mock mode due to lookup failure");
                        }
                    } else {
                        System.err.println("❌ CONFIGURATION ERROR - No GP_API_APP_KEY found in environment");
                        sendErrorResponse(response, 503, "Payment service not configured", "CONFIGURATION_ERROR");
                        return;
                    }
                }
            }
            
            // Validate card details
            if (cardDetails == null || isEmpty(cardDetails.get("brand")) || isEmpty(cardDetails.get("last4"))) {
                System.err.println("❌ CARD DETAILS VALIDATION FAILED:");
                System.err.println("   cardDetails is null: " + (cardDetails == null));
                if (cardDetails != null) {
                    System.err.println("   brand: " + cardDetails.get("brand"));
                    System.err.println("   last4: " + cardDetails.get("last4"));
                }
                sendErrorResponse(response, 400, "Invalid token or unable to retrieve card details", "VALIDATION_ERROR");
                return;
            }

            // Create payment method data using card details from token
            String expiry = cardDetails.get("expiryMonth") + "/" + cardDetails.get("expiryYear");

            Map<String, Object> paymentMethodData = new HashMap<>();
            paymentMethodData.put("vaultToken", finalToken);
            paymentMethodData.put("cardBrand", cardDetails.get("brand"));
            paymentMethodData.put("last4", cardDetails.get("last4"));
            paymentMethodData.put("expiry", expiry);
            paymentMethodData.put("nickname", nickname != null ? nickname : cardDetails.get("brand") + " ending in " + cardDetails.get("last4"));
            paymentMethodData.put("isDefault", isDefault != null ? isDefault : false);
            paymentMethodData.put("mockMode", mockMode);

            System.out.println("💾 STORING PAYMENT METHOD:");
            System.out.println("   🔐 Final Vault Token: " + finalToken.substring(0, Math.min(8, finalToken.length())) + "...");
            System.out.println("   💳 Card: " + cardDetails.get("brand") + " ending in " + cardDetails.get("last4"));
            System.out.println("   📅 Expiry: " + expiry);
            System.out.println("   📛 Nickname: " + (nickname != null ? nickname : "auto-generated"));
            System.out.println("   ⭐ Default: " + (isDefault != null ? isDefault : false));
            System.out.println("   🎭 Mock Mode: " + mockMode);

            // Save to storage
            Map<String, Object> savedMethod = JsonStorage.addPaymentMethod(paymentMethodData);

            System.out.println("✅ PAYMENT METHOD SAVED:");
            System.out.println("   🆔 Payment Method ID: " + savedMethod.get("id"));
            System.out.println("   ⏰ Timestamp: " + LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            
            // Format response
            Map<String, Object> formattedMethod = new HashMap<>();
            formattedMethod.put("id", savedMethod.get("id"));
            formattedMethod.put("brand", savedMethod.get("cardBrand"));
            formattedMethod.put("last4", savedMethod.get("last4"));
            formattedMethod.put("expiry", savedMethod.get("expiry"));
            formattedMethod.put("nickname", savedMethod.get("nickname"));
            formattedMethod.put("isDefault", savedMethod.get("isDefault"));
            formattedMethod.put("mockMode", mockMode);
            
            Map<String, Object> responseData = new HashMap<>();
            responseData.put("success", true);
            responseData.put("data", formattedMethod);
            responseData.put("message", "Payment method added successfully");
            responseData.put("timestamp", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            
            response.getWriter().write(gson.toJson(responseData));
            
        } catch (Exception e) {
            e.printStackTrace();
            sendErrorResponse(response, 500, "Payment method creation failed", "SERVER_ERROR");
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
    
    private void handleEditPaymentMethod(HttpServletResponse response, Map<String, Object> data) 
            throws IOException {
        try {
            String id = (String) data.get("id");
            
            // Find existing payment method
            Map<String, Object> existingMethod = JsonStorage.findPaymentMethod(id);
            if (existingMethod == null) {
                sendErrorResponse(response, 404, "Payment method not found", "NOT_FOUND");
                return;
            }

            // Log the edit attempt
            System.out.println("✏️ PAYMENT METHOD EDIT - Editing payment method " + id);
            System.out.println("   💳 Card: " + existingMethod.get("cardBrand") + " ending in " + existingMethod.get("last4"));
            System.out.println("   📛 Nickname: " + stringOrNone((String) existingMethod.get("nickname")) + " → " + stringOrNone((String) data.get("nickname")));
            System.out.println("   ⭐ Default: " + existingMethod.get("isDefault") + " → " + data.get("isDefault"));
            System.out.println("   ⏰ Timestamp: " + LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));

            // Update the payment method
            Map<String, Object> updateData = new HashMap<>();
            updateData.put("nickname", data.get("nickname"));
            updateData.put("isDefault", data.get("isDefault"));
            
            JsonStorage.updatePaymentMethod(id, updateData);

            // If setting as default, update all others
            Boolean isDefault = (Boolean) data.get("isDefault");
            if (Boolean.TRUE.equals(isDefault)) {
                JsonStorage.setDefaultPaymentMethod(id);
            }

            // Get the updated method
            Map<String, Object> updatedMethod = JsonStorage.findPaymentMethod(id);
            if (updatedMethod == null) {
                sendErrorResponse(response, 500, "Server error", "SERVER_ERROR");
                return;
            }

            // Log successful edit
            System.out.println("✅ 📝 PAYMENT METHOD UPDATED Successfully:");
            System.out.println("   ⏰ Timestamp: " + LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            System.out.println("   🆔 Payment Method ID: " + updatedMethod.get("id"));
            System.out.println("   💳 Card Brand: " + updatedMethod.get("cardBrand"));
            System.out.println("   🔢 Last 4: " + updatedMethod.get("last4"));
            System.out.println("   📛 Nickname: " + stringOrNone((String) updatedMethod.get("nickname")));
            System.out.println("   ⭐ Default: " + updatedMethod.get("isDefault"));
            System.out.println("   🔄 Updated: " + updatedMethod.get("updatedAt"));

            // Format response
            Map<String, Object> formattedMethod = new HashMap<>();
            formattedMethod.put("id", updatedMethod.get("id"));
            formattedMethod.put("brand", updatedMethod.get("cardBrand"));
            formattedMethod.put("last4", updatedMethod.get("last4"));
            formattedMethod.put("expiry", updatedMethod.get("expiry"));
            formattedMethod.put("nickname", updatedMethod.get("nickname"));
            formattedMethod.put("isDefault", updatedMethod.get("isDefault"));
            formattedMethod.put("mockMode", false); // Edit operations don't involve mock mode

            Map<String, Object> responseData = new HashMap<>();
            responseData.put("success", true);
            responseData.put("data", formattedMethod);
            responseData.put("message", "Payment method updated successfully");
            responseData.put("timestamp", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));

            response.getWriter().write(gson.toJson(responseData));

        } catch (Exception e) {
            System.err.println("Error updating payment method: " + e.getMessage());
            sendErrorResponse(response, 500, "Payment method update failed", "SERVER_ERROR");
        }
    }
    
    private String stringOrNone(String s) {
        return s == null || s.trim().isEmpty() ? "None" : s;
    }
}