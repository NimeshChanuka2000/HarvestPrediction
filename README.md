🍅 Harvest Prediction Backend
This is a Spring Boot backend for a smart tomato harvesting system.
It integrates IoT environment monitoring, ripeness detection, and harvest date prediction.

📌 Features
🌱 Environment Monitoring

Collects real-time environment data via MQTT (HiveMQ).

Saves and retrieves data from MongoDB.

Provides statistics (temperature, humidity, soil moisture).

🍅 Harvest Prediction

Predicts harvest date using planting date, variety, and environment data.

Stores plant details in MongoDB.

📷 Ripeness Detection

Accepts tomato images through a Flask API.

Detects ripeness stage using image processing.

🗂️ Tech Stack
Backend: Spring Boot (Java)

Database: MongoDB

IoT Broker: HiveMQ MQTT

Image Processing: Flask API (Python)

Build Tool: Maven/Gradle

🚀 API Endpoints
Environment Data

GET /api/environment/current → Get current sensor data

GET /api/environment/recent → Recent historical data

GET /api/environment/stats → Aggregated stats

GET /api/environment/device-status → Device info

Tomato Plants

POST /api/tomato/plants → Add plant

GET /api/tomato/plants → Get all plants

POST /api/tomato/plants/{id}/predict → Predict harvest date

Ripeness

POST /api/tomato/detect-ripeness → Upload image for ripeness detection

GET /api/tomato/ripeness-history → Get ripeness records

⚙️ How It Works
1️⃣ IoT device publishes environment data to HiveMQ
2️⃣ Spring Boot app receives and saves data to MongoDB
3️⃣ Image files sent to Flask API for ripeness detection
4️⃣ Harvest prediction logic combines all data for accurate results

📚 Requirements
Java 17+

MongoDB

HiveMQ Broker

Flask API for connect models backend
