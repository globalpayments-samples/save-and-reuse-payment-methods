# Save and Reuse Payment Methods System

A comprehensive multi-language demonstration of multi-use one-click payment processing using the Global Payments SDK. This example showcases secure payment method storage, multi-use token creation with integrated customer data, and streamlined payment processing across multiple programming languages.

## 🚀 Features

### Core Payment Capabilities
- **Multi-Use Token Creation** - Convert single-use tokens to stored payment tokens with customer data integration
- **Payment Method Management** - Store, retrieve, edit, and manage customer payment methods securely
- **One-Click Payment Processing** - Process immediate charges ($25) using stored payment methods
- **Customer Data Integration** - Associate billing information directly with payment tokens
- **Real-Time Processing** - Immediate transaction processing with live API integration

### Development & Testing
- **Mock Mode Support** - Test payment flows with simulated responses without hitting live APIs
- **Test Card Integration** - Built-in Heartland certification test cards for development
- **Comprehensive Web Interface** - Complete UI with payment method management and transaction processing
- **Global Payments SDK Integration** - Secure tokenization using hosted payment fields

### Technical Features
- **Consistent API Structure** - Identical endpoints and functionality across all language implementations
- **JSON-Based Storage** - Simple file-based persistence for payment methods and customer data
- **Environment Configuration** - Secure credential management with .env files
- **Health Monitoring** - System status endpoints for monitoring and debugging

## 🌐 Available Implementations

Each implementation provides identical functionality with language-specific best practices:

| Language | Framework | Requirements | Status |
|----------|-----------|--------------|--------|
| **[.NET](./dotnet/)** | ASP.NET Core | .NET 9.0+ | ✅ Complete |
| **[Java](./java/)** | Jakarta EE | Java 11+, Maven | ✅ Complete |
| **[PHP](./php/)** | Native PHP | PHP 7.4+, Composer | ✅ Complete |

## 🏗️ Architecture Overview

### Frontend Architecture
- **Global Payments SDK Integration** - Secure tokenization with hosted payment fields
- **Responsive Web Interface** - Complete payment management UI
- **Test Card Helper** - Integrated Heartland certification test cards
- **Real-Time Validation** - Client-side form validation and error handling

### Backend Architecture
- **RESTful API Design** - Consistent endpoints across all implementations
- **Multi-Use Token Creation** - Secure conversion of single-use to stored payment tokens
- **Customer Data Storage** - Integrated billing information with payment methods
- **Mock Mode Capability** - Simulated responses for development and testing

### API Endpoints

| Method | Endpoint | Description |
|--------|----------|-------------|
| `GET` | `/health` | System health check and status |
| `GET` | `/config` | Frontend configuration (public API key, mock mode) |
| `GET` | `/payment-methods` | Retrieve all stored payment methods |
| `POST` | `/payment-methods` | Create new payment method or edit existing |
| `POST` | `/charge` | Process $25 charge using stored payment method |
| `GET` | `/mock-mode` | Get current mock mode status |
| `POST` | `/mock-mode` | Toggle mock mode on/off |

## 🚀 Quick Start

### Prerequisites
- Global Payments account with API credentials ([Sign up here](https://developer.globalpay.com/))
- Development environment for your chosen language
- Package manager (npm, composer, maven, dotnet, go mod)

### Setup Instructions

1. **Clone the repository**
   ```bash
   git clone <repository-url>
   cd save-and-reuse-payment-methods
   ```

2. **Choose your implementation**
   ```bash
   cd dotnet  # or java, php
   ```

3. **Configure environment**
   ```bash
   cp .env.sample .env
   # Edit .env with your Global Payments GP API credentials:
   # GP_API_APP_ID=your_app_id
   # GP_API_APP_KEY=your_app_key
   # GP_API_ENVIRONMENT=sandbox
   ```

4. **Install dependencies and run**
   ```bash
   ./run.sh
   ```

   Or manually for each language:
   ```bash
   # .NET
   dotnet restore && dotnet run

   # Java
   mvn clean compile cargo:run

   # PHP
   composer install && php -S localhost:8000
   ```

5. **Access the application**
   Open [http://localhost:8000](http://localhost:8000) in your browser

## 🧪 Development & Testing

### Mock Mode
Each implementation includes a mock mode that allows you to:
- Test payment flows without live API calls
- Use simulated responses for consistent testing
- Develop and debug without API costs
- Toggle between mock and live modes via the UI

### Test Cards
Built-in Heartland certification test cards:
- **Visa**: 4012002000060016
- **MasterCard**: 2223000010005780, 5473500000000014
- **Discover**: 6011000990156527
- **American Express**: 372700699251018
- **JCB**: 3566007770007321

All test cards use:
- **Expiry**: 12/2028
- **CVV**: 123 (1234 for Amex)

### Customer Data
The system includes pre-filled customer information for testing:
- **Name**: Jane Doe
- **Email**: jane.doe@example.com
- **Phone**: 5551112222
- **Address**: 1 Example Way, Jeffersonville, IN 47130, USA

## 💳 Payment Flow

### Adding Payment Methods
1. **Customer Information** - Collect name, email, phone, and billing address
2. **Payment Details** - Secure tokenization using Global Payments SDK
3. **Multi-Use Token Creation** - Convert single-use token to multi-use token with customer data
4. **Storage** - Save payment method with optional nickname and default status

### Processing Payments
1. **Method Selection** - Choose from stored payment methods
2. **One-Click Processing** - Immediate $25 charge processing
3. **Real-Time Results** - Live transaction status and details
4. **Transaction History** - Complete transaction information display

## 🔧 Customization

### Extending Functionality
Each implementation provides a solid foundation for:
- **Custom Payment Amounts** - Modify charge amounts and currency
- **Additional Payment Types** - Add authorization/capture, refunds, voids
- **Enhanced Customer Management** - Expand customer data fields
- **Subscription Processing** - Recurring payment capabilities
- **Advanced Reporting** - Transaction history and analytics

### Production Considerations
Before deploying to production:
- **Security**: Implement proper input validation and sanitization
- **Logging**: Add comprehensive logging and monitoring
- **Error Handling**: Enhance error handling and user feedback
- **Database**: Replace JSON storage with production database
- **Authentication**: Add user authentication and authorization
- **Compliance**: Ensure PCI DSS compliance measures

## 📚 Documentation

Each language implementation includes detailed documentation:
- **Setup Instructions** - Environment configuration and dependencies
- **API Documentation** - Endpoint specifications and examples
- **Code Structure** - File organization and architecture
- **Troubleshooting** - Common issues and solutions
- **Recent Updates** - Bug fixes and improvements

## 🛠️ Recent Improvements

### Cross-Language Consistency
- ✅ **Unified API Structure** - Consistent endpoints across all implementations
- ✅ **Payment Token Handling** - Fixed frontend/backend data structure alignment
- ✅ **Customer Data Integration** - Standardized customer information collection
- ✅ **Mock Mode Functionality** - Consistent testing capabilities

### Bug Fixes & Enhancements
- ✅ **Java**: Fixed compilation errors and response validation with Global Payments SDK
- ✅ **.NET**: Fixed CAPTURED response validation and multi-use token creation
- ✅ **PHP**: Implemented comprehensive GP API payment system
- ✅ **All Languages**: Migrated from Portico to GP API configuration

## 🤝 Contributing

This project serves as a comprehensive example for Global Payments SDK integration. When contributing:
- Maintain consistency across all language implementations
- Follow each language's best practices and conventions
- Ensure thorough testing with both mock and live modes
- Update documentation to reflect any changes

## 📄 License

This project is provided as an educational example for Global Payments SDK integration. Please review the license file for specific terms and conditions.

## 🆘 Support

For technical support:
- **Global Payments Developer Documentation**: [https://developer.globalpay.com/](https://developer.globalpay.com/)
- **SDK Documentation**: Language-specific SDK guides
- **Community Support**: Developer forums and resources

---

**Note**: This is a demonstration application for development and testing purposes. For production use, implement additional security measures, error handling, and compliance requirements specific to your use case.