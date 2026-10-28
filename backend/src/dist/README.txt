GameFlow LB — Cloud Gaming Load Balancer Simulator
===================================================

Prerequisites
  • Java 21 or later

Quick Start
  Windows:   Double-click run.bat   (or run it from a terminal)
  Linux/Mac: chmod +x run.sh && ./run.sh

Then open your browser to http://localhost:8080 and press "Start Simulation".

Everything (backend API, WebSocket, and React frontend) is served from
the single JAR on port 8080. No Node.js or npm required at runtime.

Configuration
  The server listens on port 8080 by default.
  Override with:  java -Dserver.port=9090 -jar gameflow-lb.jar
