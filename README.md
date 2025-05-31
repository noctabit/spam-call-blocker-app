# Call Blocker App

Call Blocker App is a Kotlin-based application designed to help you manage and block unwanted incoming calls while maintaining a whitelist of approved contacts. It uses web scraping to enhance the call-blocking experience by integrating real-time data from popular spam-detection Spanish websites.

## Screenshots

Here are some screenshots of the Call Blocker App in action:

<div style="display: flex; gap: 10px;">
  <img src="https://i.imgur.com/YwmFXBh.jpeg" alt="Main screen" width="300">  
  <img src="https://i.imgur.com/2ezJn7k.jpeg" alt="Phone number options" width="300">  
  <img src="https://i.imgur.com/hvniKjX.jpeg" alt="Settings" width="300">  
  <img src="https://i.imgur.com/tWkV5Vk.jpeg" alt="More settings" width="300">
</div>

## Features

- **Block and Unblock Numbers**: Easily block or unblock specific phone numbers.
- **Whitelist Management**: Maintain a whitelist of phone numbers that are always allowed to reach you.
- **Web-Based Number Lookup**: Automatically look up phone numbers using two trusted Spanish websites:
  - [www.listaspam.com](https://www.listaspam.com)
  - [www.responderono.es](https://www.responderono.es)
- **Real-Time Web Scraping**: Leverage web scraping techniques to gather real-time information on whether a number should be blocked.

## Settings

- **General Blocking**: Toggle to enable or disable call blocking.
- **Filter by ListaSpam**: Enable filtering based on data from www.listaspam.com.
- **Filter by Responderono**: Enable filtering based on data from www.responderono.es.
- **Block Unknown Numbers**: Option to block all numbers not saved in your contacts.
- **Block Hidden Numbers**: Option to block calls from numbers with hidden caller IDs.
- **Block International Calls**: Option to block all incoming international calls.
- **Notifications**: Receive a notification whenever a call is blocked.
- **Export Preferences**: Export all app settings and blocked/whitelisted numbers.
- **Import Preferences**: Import previously exported settings and number lists.

## How It Works

The app uses the JSoup library for web scraping, which allows it to retrieve and parse HTML data from www.listaspam.com and www.responderono.es. This data is then processed to determine whether an incoming call should be blocked or not.

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
