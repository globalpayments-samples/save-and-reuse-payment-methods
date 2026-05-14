# Save and Reuse Payment Methods

> Multi-language demonstration of payment method storage and one-click payment processing using the Global Payments SDK — converting single-use tokens to stored multi-use tokens across PHP, Java, and .NET.

## Critical Patterns

1. **Card-save flows use `Verify` + `withRequestMultiUseToken(true)` — not a minimal charge.**
   `Verify` performs a card brand-approved $0 auth. A `$0.01` charge for tokenization is misuse of the authorization flow and incurs interchange penalties on the merchant.
   If card-save happens alongside an initial purchase, `Charge` with `withRequestMultiUseToken(true)` works too — it captures payment and returns the multi-use token in one call.

2. **Credentials on File (COF) flags are required on both the Verify and every subsequent charge.**
   The initial `Verify` must set `StoredCredential` to `Unscheduled / CardHolder / First`. The `SchemeId` (network transaction ID) returned in the response must be stored and passed as `schemeId` on every subsequent merchant-initiated charge (`Unscheduled / Merchant / Subsequent`). Missing these flags violates Visa/Mastercard/Amex mandates and can cause downstream declines. See `createMultiUseTokenWithCustomer()` and `processPaymentWithSDK()` in each `PaymentUtils` file.

3. **The `PaymentUtils` files are the reference implementations for each language.** If you're adapting this for your own stack, start with `createMultiUseTokenWithCustomer()` for token creation and `configureSdk()` for GP API initialization — those two methods contain the patterns most worth copying.

4. **Mock mode activates automatically when no GP API credentials are present.** Mock responses mirror real API shapes so the app behaves identically without sandbox credentials.

## Repository Structure

### PHP (Native PHP + Composer)
- [`php/PaymentUtils.php`](php/PaymentUtils.php) — SDK init (`configureSdk()`), token creation (`createMultiUseTokenWithCustomer()`), charge processing (`processPaymentWithSDK()`)
- [`php/payment-methods.php`](php/payment-methods.php) — token creation endpoint
- [`php/charge.php`](php/charge.php) — payment processing endpoint
- [`php/JsonStorage.php`](php/JsonStorage.php) — file-based storage
- [`php/MockResponses.php`](php/MockResponses.php) — simulated API responses

### Java (Jakarta EE + Maven)
- [`java/src/main/java/com/globalpayments/example/PaymentUtils.java`](java/src/main/java/com/globalpayments/example/PaymentUtils.java) — SDK init (`configureSdk()`), token creation (`createMultiUseTokenWithCustomer()`), charge processing (`processPaymentWithSDK()`)
- [`java/src/main/java/com/globalpayments/example/PaymentMethodsServlet.java`](java/src/main/java/com/globalpayments/example/PaymentMethodsServlet.java) — token creation servlet
- [`java/src/main/java/com/globalpayments/example/ChargeServlet.java`](java/src/main/java/com/globalpayments/example/ChargeServlet.java) — payment processing servlet
- [`java/src/main/java/com/globalpayments/example/ConfigServlet.java`](java/src/main/java/com/globalpayments/example/ConfigServlet.java) — frontend config servlet
- [`java/src/main/java/com/globalpayments/example/MockModeServlet.java`](java/src/main/java/com/globalpayments/example/MockModeServlet.java) — mock mode toggle servlet

### .NET (ASP.NET Core)
- [`dotnet/PaymentUtils.cs`](dotnet/PaymentUtils.cs) — async token creation (`CreateMultiUseTokenWithCustomerAsync()`)
- [`dotnet/Program.cs`](dotnet/Program.cs) — SDK init (`ConfigureGlobalPaymentsSDK()`), all REST endpoints (`ConfigureEndpoints()`)
- [`dotnet/Models.cs`](dotnet/Models.cs) — request/response models
- [`dotnet/JsonStorage.cs`](dotnet/JsonStorage.cs) — file-based storage
- [`dotnet/MockResponses.cs`](dotnet/MockResponses.cs) — simulated API responses

### Shared
- [`docker-compose.yml`](docker-compose.yml) — PHP :8003, Java :8004, .NET :8006 (host) → internal :8000 each
- [`php/composer.json`](php/composer.json), [`java/pom.xml`](java/pom.xml), [`dotnet/dotnet.csproj`](dotnet/dotnet.csproj) — dependency manifests

## API Surface

All three implementations expose identical endpoints:

| Method | Path | Purpose |
|--------|------|---------|
| GET | `/health` | System status |
| GET | `/config` | Frontend SDK configuration |
| GET | `/payment-methods` | Retrieve stored payment methods |
| POST | `/payment-methods` | Convert single-use → multi-use token |
| POST | `/charge` | Process $25 charge with stored token |
| GET | `/mock-mode` | Get mock mode status |
| POST | `/mock-mode` | Toggle mock mode on/off |

## Environment Variables

Each language has a `.env.sample` to copy from. All three read the same variables:

```bash
GP_API_APP_ID=your_app_id      # From developer.globalpayments.com
GP_API_APP_KEY=your_app_key
GP_API_ENVIRONMENT=sandbox     # sandbox or production
```

> Note: `docker-compose.yml` passes `PUBLIC_API_KEY`/`SECRET_API_KEY` instead of the `GP_API_*` vars above. The per-language `.env` files are the correct configuration path when running outside Docker.

## Sandbox Test Cards

| Network | PAN | CVV |
|---------|-----|-----|
| Visa | 4263970000005262 | 123 |
| Mastercard | 5425230000004415 | 123 |
| Discover | 6011000990156527 | 123 |
| Amex | 372700699251018 | 1234 |

Any future expiration date is accepted.

## Architecture Summary

**Token creation:** Frontend hosted fields → single-use token → POST `/payment-methods` → `Verify` with `withRequestMultiUseToken(true)` + COF CIT/First flags → multi-use token + SchemeId stored with customer data.

**Payment:** Frontend requests charge by payment method ID → backend retrieves stored token + SchemeId → `Charge` with COF MIT/Subsequent + SchemeId → transaction result.

## Security Notes

This is demo code — not production-ready. JSON file storage has no encryption, no auth, and no user isolation. For production: use a proper database, add authentication, encrypt data at rest, and meet PCI DSS requirements.

## SDK Versions

- PHP: `globalpayments/php-sdk` v13.1+
- Java: `globalpayments-sdk` v14.2.20
- .NET: `GlobalPayments.Api` v9.0.16
