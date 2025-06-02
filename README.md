# Call Blocker App

Call Blocker App is a Kotlin-based application designed to help you manage and block unwanted incoming calls while maintaining a whitelist of approved contacts. It integrates multiple spam detection sources, including real-time web scraping and an external API with multi-country support.

## Screenshots

Here are some screenshots of the Call Blocker App in action:

<div style="display: flex; gap: 10px;">
  <img src="https://i.imgur.com/YwmFXBh.jpeg" alt="Main screen" width="300">  
  <img src="https://i.imgur.com/2ezJn7k.jpeg" alt="Phone number options" width="300">  
  <img src="https://i.imgur.com/hvniKjX.jpeg" alt="Settings" width="300">  
  <img src="https://i.imgur.com/tWkV5Vk.jpeg" alt="More settings" width="300">
</div>

## Features

- **Multi-Language Number Lookup via UnknownPhone API**: Lookup phone numbers internationally using the UnknownPhone API, supporting the following country numbers:
  ```
  United States, UK, Spain, France, Germany, Italy,
  Russia, Sweden, Poland, Portugal, Netherlands,
  Norway, Czech Republic, Indonesia, China, Japan,
  Israel, Turkey, Hungary, Finland, Denmark,
  Thailand, Greece, Slovakia, Romania
  ```
- **Block and Unblock Numbers**: Easily block or unblock specific phone numbers.
- **Whitelist Management**: Maintain a whitelist of phone numbers that are always allowed to reach you.
- **Web-Based Number Lookup**: Automatically look up phone numbers using two trusted Spanish websites:
  - [www.listaspam.com](https://www.listaspam.com)
  - [www.responderono.es](https://www.responderono.es)
  - [www.cleverdialer.es](https://www.cleverdialer.es)
- **Real-Time Web Scraping**: Leverage web scraping techniques to gather real-time information on whether a number should be blocked.
- **Automatic App Updates**: The app checks for updates upon launch and prompts the user to install them if available.

## Settings

- **General Blocking**: Toggle to enable or disable call blocking.
- **Filter by UnknownPhone API**: Use UnknownPhone API to check numbers based on selected language.
- **Filter by ListaSpam**: Enable filtering based on data from www.listaspam.com.
- **Filter by Responderono**: Enable filtering based on data from www.responderono.es.
- **Block Unknown Numbers**: Option to block all numbers not saved in your contacts.
- **Block Hidden Numbers**: Option to block calls from numbers with hidden caller IDs.
- **Block International Calls**: Option to block all incoming international calls.
- **Notifications**: Receive a notification whenever a call is blocked.
- **Export Preferences**: Export all app settings and blocked/whitelisted numbers.
- **Import Preferences**: Import previously exported settings and number lists.

## How It Works

The app combines multiple data sources to determine if an incoming call should be blocked:

- UnknownPhone API Integration
Sends a POST request with the phone number and selected language to the UnknownPhone API, returning a rating indicating whether the number is likely spam. Numbers with an average rating below 3 (bad or dangerous) are blocked automatically.

- Web Scraping with JSoup
Scrapes listas spam and responderono websites in real time to gather fresh data on suspicious numbers.

- User Preferences
Uses your configured settings (whitelist, block unknown, hidden, or international numbers) to customize blocking behavior.

## Data Management

- **Export**: Users can export all their preferences, including settings and black/white lists of numbers, to a file for backup or transfer to another device.
- **Import**: Previously exported data can be imported, allowing users to quickly set up the app on a new device or restore settings after a reset.

## Privacy and Security

This app is designed with user privacy in mind. All data is stored locally on the device, and the app only accesses the internet to perform web scraping for number lookup. No personal data is shared with external servers.

## Requirements

- Android 9.0 (Pie) or higher
- Internet connection for web scraping features

## Installation

1. Download the APK from the releases page or compile it by yourself.
2. Enable installation from unknown sources in your device settings.
3. Install the app and grant necessary permissions for call management and internet access.

## Contributing

We welcome contributions to this app! Please, feel free to upload your pull requests, report bugs, or suggest new features.

## License

This app is released under the MIT License. See the LICENSE file for more details.
