<?php

declare(strict_types=1);

/**
 * Charge Endpoint
 * 
 * POST /charge - Process immediate payment ($25.00)
 */

require_once 'PaymentUtils.php';
require_once 'JsonStorage.php';
require_once 'MockResponses.php';
require_once 'mock-mode.php';

// Handle CORS
PaymentUtils::handleCORS();

// Initialize SDK
PaymentUtils::configureSdk();

// Only allow POST method
if ($_SERVER['REQUEST_METHOD'] !== 'POST') {
    PaymentUtils::sendErrorResponse(405, 'Method not allowed');
}

try {
    $data = PaymentUtils::parseJsonInput();

    if (empty($data['paymentMethodId'])) {
        PaymentUtils::sendErrorResponse(400, 'Payment method ID is required', 'VALIDATION_ERROR');
    }

    $paymentMethod = JsonStorage::findPaymentMethod($data['paymentMethodId']);
    if (!$paymentMethod) {
        PaymentUtils::sendErrorResponse(404, 'Payment method not found', 'NOT_FOUND');
    }

    if (empty($paymentMethod['cardBrand']) || empty($paymentMethod['last4'])) {
        PaymentUtils::sendErrorResponse(400, 'Payment method data is incomplete', 'INVALID_PAYMENT_METHOD');
    }

    $amount = 25.00;
    $currency = 'USD';

    $transactionResult = null;
    $mockMode = MockModeConfig::isMockModeEnabled();

    if (!$mockMode && !empty($_ENV['GP_API_APP_KEY'])) {
        try {
            $transactionResult = PaymentUtils::processPaymentWithSDK($paymentMethod['vaultToken'], $amount, $currency);
        } catch (\Exception $e) {
            error_log('Global Payments SDK payment error: ' . $e->getMessage());
            // Return actual SDK error instead of falling back to mock mode
            PaymentUtils::sendErrorResponse(422, 'Payment failed: ' . $e->getMessage(), 'PAYMENT_DECLINED');
        }
    } else {
        $mockMode = true;
    }

    if ($mockMode || !$transactionResult) {
        $transactionResult = MockResponses::getPaymentResponse($amount, $data['paymentMethodId']);
    }

    $response = [
        'transactionId' => $transactionResult['transaction_id'] ?? null,
        'amount' => $transactionResult['amount'],
        'currency' => $transactionResult['currency'],
        'status' => $transactionResult['status'],
        'responseCode' => $transactionResult['response_code'] ?? null,
        'responseMessage' => $transactionResult['response_message'] ?? null,
        'timestamp' => $transactionResult['timestamp'],
        'gatewayResponse' => $transactionResult['gateway_response'] ?? null,
        'paymentMethod' => [
            'id' => $paymentMethod['id'],
            'type' => 'card',
            'brand' => $paymentMethod['cardBrand'] ?? 'Unknown',
            'last4' => $paymentMethod['last4'],
            'nickname' => $paymentMethod['nickname'] ?? ''
        ],
        'mockMode' => $mockMode
    ];

    PaymentUtils::sendSuccessResponse($response, 'Payment processed successfully');

} catch (\Exception $e) {
    error_log('Error processing charge: ' . $e->getMessage());
    PaymentUtils::sendErrorResponse(500, 'Payment processing failed', 'SERVER_ERROR');
}