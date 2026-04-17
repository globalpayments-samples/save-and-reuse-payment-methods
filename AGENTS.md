# Save and Reuse Payment Methods

> Multi-language demonstration of payment method storage and one-click payment processing using Global Payments SDK. Shows how to convert single-use tokens to multi-use stored tokens with integrated customer data across PHP, Java, and .NET implementations.

## Critical Patterns

1. **Card-save flows use `Verify` + `withRequestMultiUseToken(true)` — not a minimal charge.**
   `Verify` performs a card brand-approved $0 auth. A `$0.01` charge for tokenization is misuse of authorizations and incurs interchange penalties on the merchant.
   If card-save happens alongside an initial purchase, `Charge` with `withRequestMultiUseToken(true)` works too — it captures payment and returns the multi-use token in one call.

2. **The `PaymentUtils` files are the reference implementations for each language.** If you're adapting this for your own stack, start there — the token creation and SDK configuration patterns are the parts most worth copying.

3. **Mock mode activates automatically when no GP API credentials are present.** Mock responses mirror real API shapes, so the app behaves identically without sandbox credentials.

## Repository Structure

### PHP (Native PHP + Composer)
- [`php/PaymentUtils.php`](php/PaymentUtils.php) — **CANONICAL** SDK config (L25–38) and token creation (L83–130)
- [`php/payment-methods.php`](php/payment-methods.php) — token creation endpoint
- [`php/charge.php`](php/charge.php) — payment processing endpoint
- [`php/JsonStorage.php`](php/JsonStorage.php) — file-based storage
- [`php/MockResponses.php`](php/MockResponses.php) — simulated API responses

### Java (Jakarta EE + Maven)
- [`java/.../PaymentUtils.java`](java/src/main/java/com/globalpayments/example/PaymentUtils.java) — **CANONICAL** SDK config (L32–45) and token creation (L247–285)
- [`java/.../PaymentMethodsServlet.java`](java/src/main/java/com/globalpayments/example/PaymentMethodsServlet.java) — token creation servlet
- [`java/.../ChargeServlet.java`](java/src/main/java/com/globalpayments/example/ChargeServlet.java) — payment processing servlet

### .NET (ASP.NET Core)
- [`dotnet/PaymentUtils.cs`](dotnet/PaymentUtils.cs) — **CANONICAL** async token creation (L48–95)
- [`dotnet/Program.cs`](dotnet/Program.cs) — all REST endpoints + SDK config (L30–55)
- [`dotnet/Models.cs`](dotnet/Models.cs) — request/response models

### Shared
- [`docker-compose.yml`](docker-compose.yml) — PHP :8080, Java :8081, .NET :5000
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
| GET/POST | `/mock-mode` | Toggle mock responses |

## Environment Variables

```bash
GP_API_APP_ID=your_app_id      # From developer.globalpayments.com
GP_API_APP_KEY=your_app_key
GP_API_ENVIRONMENT=sandbox     # sandbox or production
```

## Sandbox Test Cards

| Network | PAN | CVV |
|---------|-----|-----|
| Visa | 4012002000060016 | 123 |
| Mastercard | 5473500000000014 | 123 |
| Discover | 6011000990156527 | 123 |
| Amex | 372700699251018 | 1234 |

Any future expiration date is accepted.

## Architecture Summary

**Token creation:** Frontend hosted fields → single-use token → POST `/payment-methods` → `Verify` with `withRequestMultiUseToken(true)` → multi-use token stored with customer data.

**Payment:** Frontend requests charge by payment method ID → backend retrieves stored token → `Charge` execute → transaction result.

## Security Notes

This is demo code — not production-ready. JSON file storage has no encryption, no auth, no user isolation. For production: proper database, authentication, encryption at rest, and PCI DSS compliance are required.

## SDK Versions

- PHP: `globalpayments/php-sdk` v13.1+
- Java: `globalpayments-sdk` v14.2.20+
- .NET: `GlobalPayments.Api` v9.0.16+
