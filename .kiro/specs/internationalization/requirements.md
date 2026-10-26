# Requirements: Internationalization (i18n)

## Overview
Add multi-language support to the application, starting with Spanish (default) and English, using Java ResourceBundle for string externalization.

## Actors
- User: Selects preferred language
- Application: Displays localized strings
- Translator: Adds new language translations

## Functional Requirements

### 1. Resource Bundle Structure
- 1.1 Create base messages.properties file (Spanish default)
- 1.2 Create messages_en.properties for English
- 1.3 Organize keys by feature/component (ui.*, ml.*, analysis.*)
- 1.4 Support parameterized messages with placeholders

### 2. UI Text Externalization
- 2.1 Externalize all button labels
- 2.2 Externalize all menu items
- 2.3 Externalize all dialog titles and messages
- 2.4 Externalize all table column headers
- 2.5 Externalize all tooltips
- 2.6 Externalize all status messages

### 3. Log Message Externalization
- 3.1 Externalize info/warning/error log messages
- 3.2 Keep technical details (class names, values) as parameters
- 3.3 Maintain log readability in both languages

### 4. Language Selection
- 4.1 Auto-detect system locale on startup
- 4.2 Allow manual language selection in UI
- 4.3 Persist language preference
- 4.4 Apply language change without restart (where possible)

### 5. Date/Number Formatting
- 5.1 Format dates according to locale
- 5.2 Format numbers according to locale
- 5.3 Format percentages according to locale

### 6. Validation Messages
- 6.1 Externalize all validation error messages
- 6.2 Externalize confirmation dialog messages
- 6.3 Externalize success/failure notifications

## Non-Functional Requirements
- Maintainability: Easy to add new languages
- Completeness: No hardcoded user-facing strings
- Consistency: Same terminology across the application
- Fallback: Missing translations fall back to Spanish
