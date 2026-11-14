# Vehicle Parking Management System (C++17 & Java 17)

A multi-level, multi-row Vehicle Parking Management System developed in both **C++17** and **Java 17**, featuring nested OOP classes, dynamic data structures, search/sort algorithms, Producer-Consumer IPC, a 2D Drag-and-Drop GUI, and a one-way Java TCP Socket demonstration.

## Project Structure
- `/cpp-core/ParkingSystem.cpp` — C++17 implementation (nested `vehicle` classes, Doubly Linked List, BST, Hash Table, Binary Search, Merge Sort, Shared Memory & Message Passing IPC).
- `/java-gui-network/ParkingManagementApp.java` — Java 17 implementation (nested `vehicle` classes, data structures, IPC, 2D Swing Drag-and-Drop GUI with Help menu, and one-way TCP Client-Server sockets).
- `/docs/Vehicle_Parking_Management_Handover.pptx` — Final handover presentation slides.

## How to Compile & Run

### 1. C++17 Build
```bash
g++ -std=c++17 -pthread cpp-core/ParkingSystem.cpp -o ParkingSystem
./ParkingSystem
```

### 2. Java 17 Build
```bash
javac java-gui-network/ParkingManagementApp.java
java -cp java-gui-network ParkingManagementApp
```
