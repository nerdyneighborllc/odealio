# Amazon Used Price Search (Android)

This repository contains a simple Android app (Kotlin + Jetpack Compose) that searches Amazon for **used-condition listings** and shows parsed prices.

## Features

- Search bar for product keywords
- Scrapes Amazon search results filtered to used condition
- Displays listing title, used price, and shipping text (when present)
- Tap a result to open the Amazon listing in your browser

## Project structure

- `app/src/main/java/com/odealio/amazonusedsearch/MainActivity.kt` contains the UI and search logic
- `app/src/main/AndroidManifest.xml` defines app permissions and entry activity

## Notes

- This app relies on parsing Amazon HTML and may break when Amazon changes markup.
- Some regions or IPs may be blocked/rate limited by Amazon.
- For production use, prefer a supported pricing API.
