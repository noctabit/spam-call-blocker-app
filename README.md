# ListaSpam App

ListaSpam App is a Kotlin-based application designed to help you manage and block unwanted incoming calls while maintaining a whitelist of approved contacts. It uses web scraping to enhance the call-blocking experience by integrating real-time data from popular spam-detection spanish websites.

## Features

- **Block and Unblock Numbers**: Easily block or unblock specific phone numbers.
- **Whitelist Management**: Maintain a whitelist of phone numbers that are always allowed to reach you.
- **Web-Based Number Lookup**: Automatically look up phone numbers using two trusted spanish websites:
  - [listaspam.com](https://www.listaspam.com)
  - [Responderono.es](https://www.responderono.es)
- **Real-Time Web Scraping**: Leverage web scraping techniques to gather real-time information on whether a number should be blocked.

### Settings

- **General Blocking**: Toggle to enable or disable call blocking.
- **Filter by listaspam**: Enable filtering based on data from listaspam.com.
- **Filter by Responderono**: Enable filtering based on data from ResponderONo.es.
- **Block Unknown Numbers**: Option to block all numbers not saved in your contacts.
- **Notifications**: Receive a notification whenever a call is blocked.

## How It Works

The app uses the JSoup library for web scraping, which allows it to retrieve and parse HTML data from listaspam.com and responderono.es. This data is then processed to determine whether an incoming call should be blocked or not.
