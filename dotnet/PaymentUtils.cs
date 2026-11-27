using GlobalPayments.Api.Entities;
using GlobalPayments.Api.Entities.Enums;
using GlobalPayments.Api.PaymentMethods;
using System.Text.RegularExpressions;

namespace CardPaymentSample;

/// <summary>
/// Payment utility functions for Global Payments SDK
/// </summary>
public static class PaymentUtils
{
    /// <summary>
    /// Sanitize postal code by removing invalid characters
    /// </summary>
    public static string SanitizePostalCode(string? postalCode)
    {
        if (string.IsNullOrEmpty(postalCode))
            return string.Empty;

        var sanitized = Regex.Replace(postalCode, "[^a-zA-Z0-9-]", "");
        return sanitized.Length > 10 ? sanitized[..10] : sanitized;
    }

    /// <summary>
    /// Determine card brand from Global Payments card type
    /// </summary>
    public static string DetermineCardBrandFromType(string cardType)
    {
        if (string.IsNullOrEmpty(cardType)) return "Unknown";

        var type = cardType.ToLower();

        return type switch
        {
            "visa" => "Visa",
            "mastercard" or "mc" => "Mastercard",
            "amex" or "americanexpress" => "American Express",
            "discover" => "Discover",
            "jcb" => "JCB",
            _ => "Unknown"
        };
    }

    /// <summary>
    /// Switch a verified single use token to multi use with the SDK's UpdateToken()
    /// </summary>
    private static string MakeTokenMultiUse(CreditCardData card, CardDetails cardDetails)
    {
        // GP API wants the card expiry on a token update
        if (int.TryParse(cardDetails.ExpiryMonth, out var expMonth)) card.ExpMonth = expMonth;
        if (int.TryParse(cardDetails.ExpiryYear, out var expYear)) card.ExpYear = expYear < 100 ? 2000 + expYear : expYear;

        var update = card.UpdateToken()
            .WithPaymentMethodUsageMode(PaymentMethodUsageMode.Multiple)
            .Execute();

        if (update.TokenUsageMode != PaymentMethodUsageMode.Multiple)
        {
            throw new Exception("Multi-use token creation failed: GP API did not make the token multi use");
        }
        return card.Token;
    }

    /// <summary>
    /// Create multi-use token with customer data attached (GP API)
    /// Uses Verify to convert single-use to multi-use token (card brand-approved $0 auth)
    /// </summary>
    public static async Task<MultiUseTokenResult> CreateMultiUseTokenWithCustomerAsync(string paymentToken, CustomerData customerData, CardDetails cardDetails)
    {
        return await Task.Run(() =>
        {
            try
            {
                var card = new CreditCardData
                {
                    Token = paymentToken,
                    CardHolderName = $"{customerData.FirstName?.Trim() ?? ""} {customerData.LastName?.Trim() ?? ""}".Trim()
                };

                // Create address from customer data
                var address = new Address
                {
                    StreetAddress1 = customerData.StreetAddress?.Trim() ?? "",
                    City = customerData.City?.Trim() ?? "",
                    Province = customerData.State?.Trim() ?? "",
                    PostalCode = SanitizePostalCode(customerData.BillingZip),
                    Country = customerData.Country?.Trim() ?? ""
                };

                // Verify to convert single-use to multi-use token
                // Using Verify (not a minimal charge) is the card brand-approved method for card-save flows.
                // Flag as Cardholder Initiated (CIT), First in sequence — required by Visa/Mastercard/Amex
                // for Credentials on File (COF) compliance. Store the returned card brand reference
                // and send it with every subsequent merchant-initiated charge.
                var storedCredential = new StoredCredential
                {
                    Type = StoredCredentialType.Unscheduled,
                    Initiator = StoredCredentialInitiator.CardHolder,
                    Sequence = StoredCredentialSequence.First
                };

                var response = card.Verify()
                    .WithCurrency("USD")
                    .WithRequestMultiUseToken(true)
                    .WithStoredCredential(storedCredential)
                    .WithAddress(address)
                    .Execute();

                if (response.ResponseCode == "SUCCESS" &&
                    response.ResponseMessage == "VERIFIED")
                {
                    var brand = DetermineCardBrandFromType(cardDetails.CardType ?? "");
                    // The .NET SDK sends no storage_mode on a Verify of a token, so GP API returns no
                    // new token and the one from the form stays single use. Switch it to multi use.
                    var multiUseToken = response.Token;
                    if (string.IsNullOrEmpty(multiUseToken))
                    {
                        multiUseToken = MakeTokenMultiUse(card, cardDetails);
                    }

                    return new MultiUseTokenResult
                    {
                        MultiUseToken = multiUseToken,
                        Brand = brand,
                        Last4 = cardDetails.CardLast4 ?? "",
                        ExpiryMonth = cardDetails.ExpiryMonth ?? "",
                        ExpiryYear = cardDetails.ExpiryYear ?? "",
                        CustomerData = customerData,
                        NetworkTransactionId = response.CardBrandTransactionId  // Store for COF on subsequent charges
                    };
                }
                else
                {
                    throw new Exception($"Multi-use token creation failed: {response.ResponseMessage ?? "Unknown error"}");
                }
            }
            catch (Exception ex)
            {
                Console.Error.WriteLine($"Multi-use token creation error: {ex.Message}");
                throw;
            }
        });
    }
}