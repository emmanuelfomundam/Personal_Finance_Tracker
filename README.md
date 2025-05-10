# Personal Finance Tracker

![Java](https://img.shields.io/badge/Java-17%2B-blue)
![SQLite](https://img.shields.io/badge/SQLite-3.36%2B-green)

A comprehensive Java Swing application for managing personal finances, featuring transaction tracking, budget management, reminders, and visual reports.

## Features

- **Transaction Management**
  - Add/Edit/Delete income and expenses
  - Filter transactions by date range or keywords
  - CSV/PDF export capabilities
- **Budget Tracking**
  - Set spending limits per category
  - Visual progress bars for budget utilization
- **Smart Reminders**
  - Payment due date tracking
  - Daily automatic reminder checks
- **Visual Analytics**
  - Spending breakdown charts
  - Period-over-period comparisons
- **Database Integration**
  - SQLite backend for data persistence
  - Automatic daily backups
- **Category Management**
  - Customizable spending categories
  - Dynamic category filters

## Installation

### Prerequisites
- Java 17+ JDK
- [SQLite JDBC Driver](https://github.com/xerial/sqlite-jdbc)

### Steps
1. Clone the repository:
   ```bash
   git clone https://github.com/yourusername/finance-tracker.git

2. Add SQLite JDBC driver to classpath:
   ```bash
   cp sqlite-jdbc-3.40.0.0.jar lib/

3. Compile and run:
   ```bash
   javac -cp lib/*:. FinanceTracker.java
   java -cp lib/*:. FinanceTracker
