import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableRowSorter;
import javax.swing.RowFilter;
import java.awt.*;
import java.io.FileWriter;
import java.io.IOException;
import java.io.*;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

// Domain classes now implement Serializable for data persistence
class Transaction implements Serializable {
    private double amount;
    private String type;
    private String category;
    private LocalDate date;
    private String description;

    public Transaction(double amount, String type, String category, LocalDate date, String description) {
        this.amount = amount;
        this.type = type;
        this.category = category;
        this.date = date;
        this.description = description;
    }

    public Object[] toTableRow() {
        return new Object[]{date, type, category, String.format("$%.2f", amount), description};
    }

    public double getAmount() { return amount; }
    public String getType() { return type; }
    public String getCategory() { return category; }
    public LocalDate getDate() { return date; }
}

class Budget implements Serializable {
    private String category;
    private double limit;
    private double spent;

    public Budget(String category, double limit) {
        this.category = category;
        this.limit = limit;
    }

    public void addExpense(double amount) {
        spent += amount;
    }

    public String getCategory() { return category; }
    public double getLimit() { return limit; }
    public double getSpent() { return spent; }
    public double getRemaining() { return limit - spent; }
}

class Reminder implements Serializable {
    private LocalDate dueDate;
    private String description;
    private boolean paid;

    public Reminder(LocalDate dueDate, String description) {
        this.dueDate = dueDate;
        this.description = description;
    }

    public Object[] toTableRow() {
        String status = paid ? "Paid" : "Pending";
        return new Object[]{dueDate, description, status};
    }

    public LocalDate getDueDate() { return dueDate; }
    public String getDescription() { return description; }
    public boolean isPaid() { return paid; }
    public void markPaid() { paid = true; }
}

public class FinanceTracker extends JFrame {
    // Existing data stores
    private List<Transaction> transactions = new ArrayList<>();
    private Map<String, Budget> budgets = new HashMap<>();
    private List<Reminder> reminders = new ArrayList<>();
    private DefaultTableModel transactionModel = new DefaultTableModel();
    private DefaultTableModel reminderModel = new DefaultTableModel();
    // Categories list (used in new Category Management tab)
    private List<String> categories = new ArrayList<>(Arrays.asList(
        "Groceries", "Rent", "Entertainment", "Utilities", "Salary", "Bonus"
    ));
    private ChartPanel graphPanel;
    
    public FinanceTracker() {
        setTitle("Personal Finance Tracker");
        setSize(1200, 800);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        initializeUI();
        initializeDatabase();
        setupDailyReminderCheck();
    }
    
    private Connection getConnection() throws SQLException {
        // Using SQLite as an example; this creates/opens a file named "finance_tracker.db"
        return DriverManager.getConnection("jdbc:sqlite:finance_tracker.db");
    }

    private void initializeUI() {
        JTabbedPane tabbedPane = new JTabbedPane();
        tabbedPane.addTab("Transactions", createTransactionsPanel());
        tabbedPane.addTab("Budgets", createBudgetsPanel());
        tabbedPane.addTab("Reports", createReportsPanel());
        tabbedPane.addTab("Reminders", createRemindersPanel());
        
        tabbedPane.addTab("Advanced Transactions", createAdvancedTransactionsPanel());
        tabbedPane.addTab("Charts", createChartsPanel());
        tabbedPane.addTab("Category Management", createCategoryManagementPanel());
        
        add(tabbedPane, BorderLayout.CENTER);
        setJMenuBar(createMenuBar());
        styleComponents();
    }
    
    private void initializeDatabase() {
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {
             
        	stmt.execute("CREATE TABLE IF NOT EXISTS transactions ("
        	        + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
        	        + "date TEXT, "
        	        + "type TEXT, "
        	        + "category TEXT, "
        	        + "amount REAL, "
        	        + "description TEXT)");
        	stmt.execute("CREATE TABLE IF NOT EXISTS budgets ("
        	        + "category TEXT PRIMARY KEY, "
        	        + "limit_amount REAL, "
        	        + "spent REAL)");
        	stmt.execute("CREATE TABLE IF NOT EXISTS reminders ("
        	        + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
        	        + "dueDate TEXT, "
        	        + "description TEXT, "
        	        + "paid INTEGER)");
             stmt.execute("CREATE TABLE IF NOT EXISTS categories ("
                     + "category TEXT PRIMARY KEY)");
        } catch (SQLException ex) {
            ex.printStackTrace();
        }
    }
    
    private JPanel createTransactionsPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        JTable transactionTable = new JTable(transactionModel);
        transactionModel.setColumnIdentifiers(new String[]{"ID", "Date", "Type", "Category", "Amount", "Description"}); // Added "ID"
        transactionTable.removeColumn(transactionTable.getColumnModel().getColumn(0)); // Hide ID column

        JToolBar toolbar = new JToolBar();

        JButton addButton = new JButton("Add Transaction");
        addButton.addActionListener(e -> showTransactionDialog());
        toolbar.add(addButton);

        JButton editButton = new JButton("Edit Transaction");
        editButton.addActionListener(e -> editSelectedTransaction(transactionTable, transactionModel));
        toolbar.add(editButton);

        JButton deleteButton = new JButton("Delete Transaction");
        deleteButton.addActionListener(e -> deleteSelectedTransaction(transactionTable, transactionModel));
        toolbar.add(deleteButton);

        JButton refreshButton = new JButton("Refresh");
        refreshButton.addActionListener(e -> refreshTransactionTable(transactionModel));
        toolbar.add(refreshButton);

        panel.add(toolbar, BorderLayout.NORTH);
        panel.add(new JScrollPane(transactionTable), BorderLayout.CENTER);
        return panel;
    }
    
    private void editSelectedTransaction(JTable transactionTable, DefaultTableModel transactionModel) {
        int selectedRow = transactionTable.getSelectedRow();
        if (selectedRow == -1) {
            JOptionPane.showMessageDialog(null, "Select a transaction to edit.");
            return;
        }

        // Retrieve selected transaction data
        int transactionId = (int) transactionModel.getValueAt(selectedRow, 0); // ID column (hidden)
        String date = (String) transactionModel.getValueAt(selectedRow, 1);
        String type = (String) transactionModel.getValueAt(selectedRow, 2);
        String category = (String) transactionModel.getValueAt(selectedRow, 3);
        double amount = (double) transactionModel.getValueAt(selectedRow, 4);
        String description = (String) transactionModel.getValueAt(selectedRow, 5);

        // Show input dialogs for editing
        String newDate = JOptionPane.showInputDialog(null, "Enter new date (YYYY-MM-DD):", date);
        if (newDate == null || newDate.trim().isEmpty()) return;

        String newType = JOptionPane.showInputDialog(null, "Enter new type (Income/Expense):", type);
        if (newType == null || newType.trim().isEmpty()) return;

        String newCategory = JOptionPane.showInputDialog(null, "Enter new category:", category);
        if (newCategory == null || newCategory.trim().isEmpty()) return;

        String newAmountStr = JOptionPane.showInputDialog(null, "Enter new amount:", amount);
        if (newAmountStr == null || newAmountStr.trim().isEmpty()) return;

        String newDescription = JOptionPane.showInputDialog(null, "Enter new description:", description);
        if (newDescription == null) newDescription = ""; // Allow empty description

        try {
            double newAmount = Double.parseDouble(newAmountStr);

            // Update in database
            try (Connection conn = DriverManager.getConnection("jdbc:sqlite:finance_tracker.db");
                 PreparedStatement stmt = conn.prepareStatement("UPDATE transactions SET date = ?, type = ?, category = ?, amount = ?, description = ? WHERE id = ?")) {
                stmt.setString(1, newDate);
                stmt.setString(2, newType);
                stmt.setString(3, newCategory);
                stmt.setDouble(4, newAmount);
                stmt.setString(5, newDescription);
                stmt.setInt(6, transactionId);
                stmt.executeUpdate();
                JOptionPane.showMessageDialog(null, "Transaction updated successfully.");
            }
            
            // Refresh the table
            refreshTransactionTable(transactionModel);
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(null, "Invalid amount value. Please enter a number.");
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(null, "Error updating transaction: " + ex.getMessage());
        }
    }
    
    private void deleteSelectedTransaction(JTable transactionTable, DefaultTableModel transactionModel) {
        int selectedRow = transactionTable.getSelectedRow();
        if (selectedRow == -1) {
            JOptionPane.showMessageDialog(null, "Select a transaction to delete.");
            return;
        }

        int transactionId = (int) transactionModel.getValueAt(selectedRow, 0);

        int confirm = JOptionPane.showConfirmDialog(null, "Are you sure you want to delete this transaction?", "Confirm Delete", JOptionPane.YES_NO_OPTION);
        if (confirm == JOptionPane.YES_OPTION) {
            try (Connection conn = DriverManager.getConnection("jdbc:sqlite:finance_tracker.db");
                 PreparedStatement stmt = conn.prepareStatement("DELETE FROM transactions WHERE id = ?")) {
                stmt.setInt(1, transactionId);
                stmt.executeUpdate();
                JOptionPane.showMessageDialog(null, "Transaction deleted successfully.");
            } catch (SQLException ex) {
                JOptionPane.showMessageDialog(null, "Error deleting transaction: " + ex.getMessage());
            }
            
            // Refresh the table
            refreshTransactionTable(transactionModel);
        }
    }private void refreshTransactionTable(DefaultTableModel transactionModel) {
        transactionModel.setRowCount(0); // Clear existing data

        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:finance_tracker.db");
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT id, date, type, category, amount, description FROM transactions")) {
            
            while (rs.next()) {
                transactionModel.addRow(new Object[]{
                    rs.getInt("id"),
                    rs.getString("date"),
                    rs.getString("type"),
                    rs.getString("category"),
                    rs.getDouble("amount"),
                    rs.getString("description")
                });
            }
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(null, "Error loading transactions: " + ex.getMessage());
        }
    }
    
    private JPanel createBudgetsPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        DefaultTableModel budgetModel = new DefaultTableModel();
        JTable budgetTable = new JTable(budgetModel);
        budgetModel.setColumnIdentifiers(new String[]{"ID", "Category", "Limit", "Spent", "Remaining"}); // Added "ID" for reference
        budgetTable.removeColumn(budgetTable.getColumnModel().getColumn(0)); // Hide ID column

        JToolBar toolbar = new JToolBar();

        JButton addBudgetButton = new JButton("Set Budget");
        addBudgetButton.addActionListener(e -> showBudgetDialog());
        toolbar.add(addBudgetButton);

        // New Edit and Delete Buttons
        JButton editBudgetButton = new JButton("Edit Budget");
        editBudgetButton.addActionListener(e -> editSelectedBudget(budgetTable, budgetModel));
        toolbar.add(editBudgetButton);

        JButton deleteBudgetButton = new JButton("Delete Budget");
        deleteBudgetButton.addActionListener(e -> deleteSelectedBudget(budgetTable, budgetModel));
        toolbar.add(deleteBudgetButton);
        
        JButton refreshButton = new JButton("Refresh");
        refreshButton.addActionListener(e -> refreshBudgetTable(budgetModel));
        toolbar.add(refreshButton);

        JButton progressButton = new JButton("Show Budget Progress");
        progressButton.addActionListener(e -> showBudgetProgress());
        toolbar.add(progressButton);

        panel.add(toolbar, BorderLayout.NORTH);
        panel.add(new JScrollPane(budgetTable), BorderLayout.CENTER);

        return panel;
    }
    
    private void editSelectedBudget(JTable budgetTable, DefaultTableModel budgetModel) {
        int selectedRow = budgetTable.getSelectedRow();
        if (selectedRow == -1) {
            JOptionPane.showMessageDialog(null, "Select a budget to edit.");
            return;
        }

        // Retrieve selected budget data
        int budgetId = (int) budgetModel.getValueAt(selectedRow, 0); // ID column (hidden)
        String category = (String) budgetModel.getValueAt(selectedRow, 1);
        double limit = (double) budgetModel.getValueAt(selectedRow, 2);
        
        // Show input dialogs for editing
        String newCategory = JOptionPane.showInputDialog(null, "Enter new category:", category);
        if (newCategory == null || newCategory.trim().isEmpty()) return;

        String newLimitStr = JOptionPane.showInputDialog(null, "Enter new limit:", limit);
        if (newLimitStr == null || newLimitStr.trim().isEmpty()) return;

        try {
            double newLimit = Double.parseDouble(newLimitStr);

            // Update in database
            try (Connection conn = DriverManager.getConnection("jdbc:sqlite:finance_tracker.db");
                 PreparedStatement stmt = conn.prepareStatement("UPDATE budgets SET category = ?, limit = ? WHERE id = ?")) {
                stmt.setString(1, newCategory);
                stmt.setDouble(2, newLimit);
                stmt.setInt(3, budgetId);
                stmt.executeUpdate();
                JOptionPane.showMessageDialog(null, "Budget updated successfully.");
            }
            
            // Refresh the table
            refreshBudgetTable(budgetModel);
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(null, "Invalid limit value. Please enter a number.");
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(null, "Error updating budget: " + ex.getMessage());
        }
    }

    private void deleteSelectedBudget(JTable budgetTable, DefaultTableModel budgetModel) {
        int selectedRow = budgetTable.getSelectedRow();
        if (selectedRow == -1) {
            JOptionPane.showMessageDialog(null, "Select a budget to delete.");
            return;
        }

        int budgetId = (int) budgetModel.getValueAt(selectedRow, 0);

        int confirm = JOptionPane.showConfirmDialog(null, "Are you sure you want to delete this budget?", "Confirm Delete", JOptionPane.YES_NO_OPTION);
        if (confirm == JOptionPane.YES_OPTION) {
            try (Connection conn = DriverManager.getConnection("jdbc:sqlite:finance_tracker.db");
                 PreparedStatement stmt = conn.prepareStatement("DELETE FROM budgets WHERE id = ?")) {
                stmt.setInt(1, budgetId);
                stmt.executeUpdate();
                JOptionPane.showMessageDialog(null, "Budget deleted successfully.");
            } catch (SQLException ex) {
                JOptionPane.showMessageDialog(null, "Error deleting budget: " + ex.getMessage());
            }
            
            // Refresh the table
            refreshBudgetTable(budgetModel);
        }
    }

    
    private JPanel createReportsPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        JPanel buttonPanel = new JPanel();
        JButton reportButton = new JButton("Show Spending Report");
        reportButton.addActionListener(e -> showSpendingChart());
        buttonPanel.add(reportButton);
        
        // New button for period comparison
        JButton compareButton = new JButton("Compare Periods");
        compareButton.addActionListener(e -> showPeriodComparison());
        buttonPanel.add(compareButton);
        
        panel.add(buttonPanel, BorderLayout.NORTH);
        return panel;
    }
    
    private JPanel createRemindersPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        JTable reminderTable = new JTable(reminderModel);
        reminderModel.setColumnIdentifiers(new String[]{"ID", "Due Date", "Description", "Status"}); // Added "ID"
        reminderTable.removeColumn(reminderTable.getColumnModel().getColumn(0)); // Hide ID column

        JToolBar toolbar = new JToolBar();

        JButton addReminderButton = new JButton("Add Reminder");
        addReminderButton.addActionListener(e -> showReminderDialog());
        toolbar.add(addReminderButton);

        JButton editReminderButton = new JButton("Edit Reminder");
        editReminderButton.addActionListener(e -> editSelectedReminder(reminderTable, reminderModel));
        toolbar.add(editReminderButton);

        JButton deleteReminderButton = new JButton("Delete Reminder");
        deleteReminderButton.addActionListener(e -> deleteSelectedReminder(reminderTable, reminderModel));
        toolbar.add(deleteReminderButton);

        JButton refreshButton = new JButton("Refresh");
        refreshButton.addActionListener(e -> refreshReminderTable(reminderModel));
        toolbar.add(refreshButton);

        panel.add(toolbar, BorderLayout.NORTH);
        panel.add(new JScrollPane(reminderTable), BorderLayout.CENTER);
        return panel;
    }
    
    private void editSelectedReminder(JTable reminderTable, DefaultTableModel reminderModel) {
        int selectedRow = reminderTable.getSelectedRow();
        if (selectedRow == -1) {
            JOptionPane.showMessageDialog(null, "Select a reminder to edit.");
            return;
        }

        // Retrieve selected reminder data
        int reminderId = (int) reminderModel.getValueAt(selectedRow, 0); // ID column (hidden)
        String dueDate = (String) reminderModel.getValueAt(selectedRow, 1);
        String description = (String) reminderModel.getValueAt(selectedRow, 2);
        String status = (String) reminderModel.getValueAt(selectedRow, 3);

        // Show input dialogs for editing
        String newDueDate = JOptionPane.showInputDialog(null, "Enter new due date (YYYY-MM-DD):", dueDate);
        if (newDueDate == null || newDueDate.trim().isEmpty()) return;

        String newDescription = JOptionPane.showInputDialog(null, "Enter new description:", description);
        if (newDescription == null) newDescription = "";

        String[] statusOptions = {"Pending", "Completed"};
        String newStatus = (String) JOptionPane.showInputDialog(null, "Select new status:", "Edit Status",
                JOptionPane.QUESTION_MESSAGE, null, statusOptions, status);

        if (newStatus == null || newStatus.trim().isEmpty()) return;

        try {
            // Update in database
            try (Connection conn = DriverManager.getConnection("jdbc:sqlite:finance_tracker.db");
                 PreparedStatement stmt = conn.prepareStatement("UPDATE reminders SET due_date = ?, description = ?, status = ? WHERE id = ?")) {
                stmt.setString(1, newDueDate);
                stmt.setString(2, newDescription);
                stmt.setString(3, newStatus);
                stmt.setInt(4, reminderId);
                stmt.executeUpdate();
                JOptionPane.showMessageDialog(null, "Reminder updated successfully.");
            }
            
            // Refresh the table
            refreshReminderTable(reminderModel);
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(null, "Error updating reminder: " + ex.getMessage());
        }
    }

    private void deleteSelectedReminder(JTable reminderTable, DefaultTableModel reminderModel) {
        int selectedRow = reminderTable.getSelectedRow();
        if (selectedRow == -1) {
            JOptionPane.showMessageDialog(null, "Select a reminder to delete.");
            return;
        }

        int reminderId = (int) reminderModel.getValueAt(selectedRow, 0);

        int confirm = JOptionPane.showConfirmDialog(null, "Are you sure you want to delete this reminder?", "Confirm Delete", JOptionPane.YES_NO_OPTION);
        if (confirm == JOptionPane.YES_OPTION) {
            try (Connection conn = DriverManager.getConnection("jdbc:sqlite:finance_tracker.db");
                 PreparedStatement stmt = conn.prepareStatement("DELETE FROM reminders WHERE id = ?")) {
                stmt.setInt(1, reminderId);
                stmt.executeUpdate();
                JOptionPane.showMessageDialog(null, "Reminder deleted successfully.");
            } catch (SQLException ex) {
                JOptionPane.showMessageDialog(null, "Error deleting reminder: " + ex.getMessage());
            }
            
            // Refresh the table
            refreshReminderTable(reminderModel);
        }
    }

    private void refreshReminderTable(DefaultTableModel reminderModel) {
        reminderModel.setRowCount(0); // Clear existing data

        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:finance_tracker.db");
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT id, due_date, description, status FROM reminders")) {
            
            while (rs.next()) {
                reminderModel.addRow(new Object[]{
                    rs.getInt("id"),
                    rs.getString("due_date"),
                    rs.getString("description"),
                    rs.getString("status")
                });
            }
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(null, "Error loading reminders: " + ex.getMessage());
        }
    }
    
    private JPanel createAdvancedTransactionsPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        
        // Filtering toolbar at the top
        JPanel filterPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        filterPanel.add(new JLabel("Search:"));
        JTextField searchField = new JTextField(10);
        filterPanel.add(searchField);
        
        filterPanel.add(new JLabel("From (YYYY-MM-DD):"));
        JTextField fromDateField = new JTextField(10);
        filterPanel.add(fromDateField);
        
        filterPanel.add(new JLabel("To (YYYY-MM-DD):"));
        JTextField toDateField = new JTextField(10);
        filterPanel.add(toDateField);
        
        JButton applyFilterButton = new JButton("Apply Filter");
        filterPanel.add(applyFilterButton);
        
        panel.add(filterPanel, BorderLayout.NORTH);
        
        // Transactions table (reuse existing model)
        JTable advTable = new JTable(transactionModel);
        TableRowSorter<DefaultTableModel> sorter = new TableRowSorter<>(transactionModel);
        advTable.setRowSorter(sorter);
        
        applyFilterButton.addActionListener(e -> {
            List<RowFilter<Object, Object>> filters = new ArrayList<>();
            String searchText = searchField.getText().trim();
            if (!searchText.isEmpty()) {
                filters.add(RowFilter.regexFilter("(?i)" + searchText));
            }
            String fromText = fromDateField.getText().trim();
            String toText = toDateField.getText().trim();
            if (!fromText.isEmpty() || !toText.isEmpty()) {
            	filters.add(new RowFilter<Object, Object>() {
            	    @Override
            	    public boolean include(Entry<? extends Object, ? extends Object> entry) {
            	        try {
            	            String dateStr = entry.getValue(0).toString();
            	            LocalDate date = LocalDate.parse(dateStr);
            	            boolean afterFrom = true, beforeTo = true;
            	            if (!fromText.isEmpty()) {
            	                LocalDate fromDate = LocalDate.parse(fromText);
            	                afterFrom = !date.isBefore(fromDate);
            	            }
            	            if (!toText.isEmpty()) {
            	                LocalDate toDate = LocalDate.parse(toText);
            	                beforeTo = !date.isAfter(toDate);
            	            }
            	            return afterFrom && beforeTo;
            	        } catch (Exception ex) {
            	            return false;
            	        }
            	    }
            	});
            }
            RowFilter<Object, Object> compoundRowFilter = RowFilter.andFilter(filters);
            sorter.setRowFilter(compoundRowFilter);
        });
        
        panel.add(new JScrollPane(advTable), BorderLayout.CENTER);
        
        // Edit Transaction button
        JButton editButton = new JButton("Edit Transaction");
        editButton.addActionListener(e -> {
            int selectedRow = advTable.getSelectedRow();
            if (selectedRow != -1) {
                // Convert row index due to sorter
                int modelIndex = advTable.convertRowIndexToModel(selectedRow);
                Transaction t = transactions.get(modelIndex);
                showEditTransactionDialog(t, modelIndex);
            } else {
                JOptionPane.showMessageDialog(this, "Please select a transaction to edit.");
            }
        });
        panel.add(editButton, BorderLayout.SOUTH);
        return panel;
    }
    
    private JPanel createChartsPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        JPanel buttonPanel = new JPanel();
        
        // Initialize the class-level graphPanel
        graphPanel = new ChartPanel();
        
        JButton showChartButton = new JButton("Show Chart");
        showChartButton.addActionListener(e -> {
            // This will now work
            Map<String, Double> data = calculateSpendingByCategory();
            graphPanel.updateData(data);
        });
        
        buttonPanel.add(showChartButton);
        panel.add(buttonPanel, BorderLayout.NORTH);
        panel.add(graphPanel, BorderLayout.CENTER);
        return panel;
    }
    
    private Map<String, Double> calculateSpendingByCategory() {
        Map<String, Double> spending = new HashMap<>();
        for (Transaction t : transactions) {
            if (t.getType().equals("Expense")) {
                spending.merge(t.getCategory(), t.getAmount(), Double::sum);
            }
        }
        return spending;
    }
    
    private JPanel createCategoryManagementPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        DefaultListModel<String> listModel = new DefaultListModel<>();
        categories.forEach(listModel::addElement);
        JList<String> categoryList = new JList<>(listModel);
        panel.add(new JScrollPane(categoryList), BorderLayout.CENTER);
        
        JPanel buttonPanel = new JPanel();
        JButton addButton = new JButton("Add Category");
        addButton.addActionListener(e -> {
            String newCategory = JOptionPane.showInputDialog(this, "Enter new category:");
            if (newCategory != null && !newCategory.trim().isEmpty()) {
                categories.add(newCategory.trim());
                listModel.addElement(newCategory.trim());
            }
        });
        JButton removeButton = new JButton("Remove Category");
        removeButton.addActionListener(e -> {
            int selectedIndex = categoryList.getSelectedIndex();
            if (selectedIndex != -1) {
                String cat = listModel.getElementAt(selectedIndex);
                categories.remove(cat);
                listModel.remove(selectedIndex);
            }
        });
        buttonPanel.add(addButton);
        buttonPanel.add(removeButton);
        panel.add(buttonPanel, BorderLayout.SOUTH);
        return panel;
    }
    
    private JMenuBar createMenuBar() {
        JMenuBar menuBar = new JMenuBar();
        JMenu fileMenu = new JMenu("File");
        
        JMenuItem exportCSV = new JMenuItem("Export CSV");
        exportCSV.addActionListener(e -> exportCSV());
        fileMenu.add(exportCSV);
        
        JMenuItem importCSV = new JMenuItem("Import CSV");
        importCSV.addActionListener(e -> importCSV());
        fileMenu.add(importCSV);
        
        JMenuItem exportPDF = new JMenuItem("Export PDF");
        exportPDF.addActionListener(e -> exportPDF());
        fileMenu.add(exportPDF);
        
        JMenuItem saveData = new JMenuItem("Save Data");
        saveData.addActionListener(e -> saveData());
        fileMenu.add(saveData);
        
        JMenuItem loadData = new JMenuItem("Load Data");
        loadData.addActionListener(e -> loadData());
        fileMenu.add(loadData);
        
        menuBar.add(fileMenu);
        return menuBar;
    }
    
    private void showTransactionDialog() {
        JDialog dialog = new JDialog(this, "Add Transaction", true);
        dialog.setLayout(new GridLayout(6, 2));
        
        JComboBox<String> typeCombo = new JComboBox<>(new String[]{"Income", "Expense"});
        JTextField amountField = new JTextField();
        JComboBox<String> categoryCombo = new JComboBox<>(new String[]{"Groceries", "Rent", "Entertainment", "Utilities"});
        JTextField dateField = new JTextField(LocalDate.now().toString());
        JTextField descriptionField = new JTextField();
        
        typeCombo.addActionListener(e -> {
            if (typeCombo.getSelectedItem().equals("Income")) {
                categoryCombo.setModel(new DefaultComboBoxModel<>(new String[]{"Salary", "Bonus", "Other"}));
            } else {
                categoryCombo.setModel(new DefaultComboBoxModel<>(new String[]{"Groceries", "Rent", "Entertainment", "Utilities"}));
            }
        });
        
        JButton saveButton = new JButton("Save");
        saveButton.addActionListener(e -> {
            try {
                double amount = Double.parseDouble(amountField.getText());
                String type = (String) typeCombo.getSelectedItem();
                String category = (String) categoryCombo.getSelectedItem();
                LocalDate date = LocalDate.parse(dateField.getText());
                
                Transaction t = new Transaction(amount, type, category, date, descriptionField.getText());
                transactions.add(t);
                
                if (type.equals("Expense")) {
                    budgets.computeIfPresent(category, (k, v) -> {
                        v.addExpense(amount);
                        return v;
                    });
                }
                
                transactionModel.addRow(t.toTableRow());
                dialog.dispose();
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(dialog, "Invalid input: " + ex.getMessage());
            }
        });
        
        dialog.add(new JLabel("Type:"));
        dialog.add(typeCombo);
        dialog.add(new JLabel("Amount:"));
        dialog.add(amountField);
        dialog.add(new JLabel("Category:"));
        dialog.add(categoryCombo);
        dialog.add(new JLabel("Date (YYYY-MM-DD):"));
        dialog.add(dateField);
        dialog.add(new JLabel("Description:"));
        dialog.add(descriptionField);
        dialog.add(saveButton);
        
        dialog.pack();
        dialog.setVisible(true);
    }
    
    private void showEditTransactionDialog(Transaction t, int index) {
        JDialog dialog = new JDialog(this, "Edit Transaction", true);
        dialog.setLayout(new GridLayout(6, 2));
        
        // Prepopulate fields with transaction data
        JTextField amountField = new JTextField(String.valueOf(t.getAmount()));
        JTextField typeField = new JTextField(t.getType());
        JTextField categoryField = new JTextField(t.getCategory());
        JTextField dateField = new JTextField(t.getDate().toString());
        JTextField descriptionField = new JTextField(t.toTableRow()[4].toString());
        
        dialog.add(new JLabel("Amount:"));
        dialog.add(amountField);
        dialog.add(new JLabel("Type:"));
        dialog.add(typeField);
        dialog.add(new JLabel("Category:"));
        dialog.add(categoryField);
        dialog.add(new JLabel("Date (YYYY-MM-DD):"));
        dialog.add(dateField);
        dialog.add(new JLabel("Description:"));
        dialog.add(descriptionField);
        
        JButton saveButton = new JButton("Save");
        saveButton.addActionListener(e -> {
            try {
                double amount = Double.parseDouble(amountField.getText());
                String type = typeField.getText().trim();
                String category = categoryField.getText().trim();
                LocalDate date = LocalDate.parse(dateField.getText().trim());
                String description = descriptionField.getText().trim();
                
                Transaction updated = new Transaction(amount, type, category, date, description);
                transactions.set(index, updated);
                
                // Update table model row
                for (int i = 0; i < transactionModel.getColumnCount(); i++) {
                    transactionModel.setValueAt(updated.toTableRow()[i], index, i);
                }
                dialog.dispose();
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(dialog, "Invalid input: " + ex.getMessage());
            }
        });
        
        dialog.add(saveButton);
        dialog.pack();
        dialog.setVisible(true);
    }
    
    private void showBudgetDialog() {
        JDialog dialog = new JDialog(this, "Set Budget", true);
        dialog.setLayout(new GridLayout(3, 2));
        
        JComboBox<String> categoryCombo = new JComboBox<>(
            new String[]{"Groceries", "Rent", "Entertainment", "Utilities"});
        JTextField amountField = new JTextField();
        
        dialog.add(new JLabel("Category:"));
        dialog.add(categoryCombo);
        dialog.add(new JLabel("Budget Limit:"));
        dialog.add(amountField);
        
        JButton saveButton = new JButton("Save");
        saveButton.addActionListener(e -> {
            try {
                String category = (String) categoryCombo.getSelectedItem();
                double limit = Double.parseDouble(amountField.getText());
                budgets.put(category, new Budget(category, limit));
                dialog.dispose();
            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(dialog, "Invalid amount format");
            }
        });
        
        dialog.add(saveButton);
        dialog.pack();
        dialog.setVisible(true);
    }
    
    private void showReminderDialog() {
        JDialog dialog = new JDialog(this, "Add Reminder", true);
        dialog.setLayout(new GridLayout(3, 2));
        dialog.setSize(400, 200);

        JTextField descriptionField = new JTextField();
        JTextField dateField = new JTextField(LocalDate.now().toString());
        
        dialog.add(new JLabel("Description:"));
        dialog.add(descriptionField);
        dialog.add(new JLabel("Due Date (YYYY-MM-DD):"));
        dialog.add(dateField);

        JButton saveButton = new JButton("Save");
        JButton cancelButton = new JButton("Cancel");

        saveButton.addActionListener(e -> {
            try {
                LocalDate dueDate = LocalDate.parse(dateField.getText());
                String description = descriptionField.getText().trim();
                
                if (description.isEmpty()) {
                    JOptionPane.showMessageDialog(dialog, "Description cannot be empty", "Error", JOptionPane.ERROR_MESSAGE);
                    return;
                }
                
                Reminder reminder = new Reminder(dueDate, description);
                reminders.add(reminder);
                reminderModel.addRow(reminder.toTableRow());
                dialog.dispose();
                
            } catch (DateTimeParseException ex) {
                JOptionPane.showMessageDialog(dialog, "Invalid date format. Please use YYYY-MM-DD.", "Error", JOptionPane.ERROR_MESSAGE);
            }
        });

        cancelButton.addActionListener(e -> dialog.dispose());

        dialog.add(saveButton);
        dialog.add(cancelButton);
        dialog.setVisible(true);
    }
    
    private void showSpendingChart() {
        // Text-based spending report
        Map<String, Double> categorySpending = new HashMap<>();
        transactions.stream()
            .filter(t -> t.getType().equals("Expense"))
            .forEach(t -> categorySpending.merge(t.getCategory(), t.getAmount(), Double::sum));
        
        StringBuilder report = new StringBuilder("Spending Breakdown:\n\n");
        double total = categorySpending.values().stream().mapToDouble(Double::doubleValue).sum();
        
        for (Map.Entry<String, Double> entry : categorySpending.entrySet()) {
            double percentage = (entry.getValue() / total) * 100;
            report.append(String.format("%s: $%.2f (%.1f%%)\n", 
                entry.getKey(), entry.getValue(), percentage));
        }
        
        JTextArea textArea = new JTextArea(report.toString());
        textArea.setEditable(false);
        JOptionPane.showMessageDialog(this, new JScrollPane(textArea), 
            "Spending Report", JOptionPane.INFORMATION_MESSAGE);
    }
    
    private void showASCIIChart() {
        Map<String, Double> categorySpending = new HashMap<>();
        transactions.stream()
            .filter(t -> t.getType().equals("Expense"))
            .forEach(t -> categorySpending.merge(t.getCategory(), t.getAmount(), Double::sum));
        
        StringBuilder chart = new StringBuilder("ASCII Spending Chart:\n\n");
        double max = categorySpending.values().stream().mapToDouble(Double::doubleValue).max().orElse(1);
        for (Map.Entry<String, Double> entry : categorySpending.entrySet()) {
            int barLength = (int) ((entry.getValue() / max) * 50);
            chart.append(String.format("%-15s: %s ($%.2f)\n", entry.getKey(), "*".repeat(barLength), entry.getValue()));
        }
        JOptionPane.showMessageDialog(this, new JScrollPane(new JTextArea(chart.toString())), "ASCII Chart", JOptionPane.INFORMATION_MESSAGE);
    }
    
    private void showPeriodComparison() {
        JTextField currentFrom = new JTextField(LocalDate.now().minusDays(30).toString());
        JTextField currentTo = new JTextField(LocalDate.now().toString());
        JTextField previousFrom = new JTextField(LocalDate.now().minusDays(60).toString());
        JTextField previousTo = new JTextField(LocalDate.now().minusDays(31).toString());
        
        JPanel panel = new JPanel(new GridLayout(4, 2));
        panel.add(new JLabel("Current Period From:"));
        panel.add(currentFrom);
        panel.add(new JLabel("Current Period To:"));
        panel.add(currentTo);
        panel.add(new JLabel("Previous Period From:"));
        panel.add(previousFrom);
        panel.add(new JLabel("Previous Period To:"));
        panel.add(previousTo);
        
        int result = JOptionPane.showConfirmDialog(this, panel, "Enter Date Ranges", JOptionPane.OK_CANCEL_OPTION);
        if (result == JOptionPane.OK_OPTION) {
            try {
                LocalDate curFromDate = LocalDate.parse(currentFrom.getText());
                LocalDate curToDate = LocalDate.parse(currentTo.getText());
                LocalDate prevFromDate = LocalDate.parse(previousFrom.getText());
                LocalDate prevToDate = LocalDate.parse(previousTo.getText());
                
                double currentTotal = transactions.stream()
                    .filter(t -> t.getType().equals("Expense") && !t.getDate().isBefore(curFromDate) && !t.getDate().isAfter(curToDate))
                    .mapToDouble(Transaction::getAmount)
                    .sum();
                double previousTotal = transactions.stream()
                    .filter(t -> t.getType().equals("Expense") && !t.getDate().isBefore(prevFromDate) && !t.getDate().isAfter(prevToDate))
                    .mapToDouble(Transaction::getAmount)
                    .sum();
                
                String message = String.format("Current Period Spending: $%.2f\nPrevious Period Spending: $%.2f\nDifference: $%.2f", 
                    currentTotal, previousTotal, currentTotal - previousTotal);
                JOptionPane.showMessageDialog(this, message, "Period Comparison", JOptionPane.INFORMATION_MESSAGE);
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Invalid date input.");
            }
        }
    }
    
    private void showBudgetProgress() {
        JPanel panel = new JPanel(new GridLayout(budgets.size(), 1));
        for (Budget budget : budgets.values()) {
            JPanel row = new JPanel(new BorderLayout());
            JLabel label = new JLabel(budget.getCategory() + " ($" + String.format("%.2f", budget.getSpent()) + " / $" + String.format("%.2f", budget.getLimit()) + ")");
            JProgressBar progressBar = new JProgressBar(0, (int) budget.getLimit());
            progressBar.setValue((int) budget.getSpent());
            progressBar.setStringPainted(true);
            row.add(label, BorderLayout.WEST);
            row.add(progressBar, BorderLayout.CENTER);
            panel.add(row);
        }
        JOptionPane.showMessageDialog(this, panel, "Budget Progress", JOptionPane.INFORMATION_MESSAGE);
    }
    
    private void exportCSV() {
        JFileChooser fileChooser = new JFileChooser();
        if (fileChooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            try (FileWriter fw = new FileWriter(fileChooser.getSelectedFile())) {
                fw.write("Date,Type,Category,Amount,Description\n");
                for (Transaction t : transactions) {
                    fw.write(String.format("%s,%s,%s,%.2f,%s\n", t.getDate(), t.getType(), t.getCategory(), t.getAmount(), t.toTableRow()[4]));
                }
                fw.flush();
                JOptionPane.showMessageDialog(this, "CSV exported successfully.");
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(this, "Error exporting CSV: " + ex.getMessage());
            }
        }
    }
    
    private void importCSV() {
        JFileChooser fileChooser = new JFileChooser();
        if (fileChooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            try (Scanner scanner = new Scanner(fileChooser.getSelectedFile())) {
                transactions.clear();
                transactionModel.setRowCount(0);
                if (scanner.hasNextLine()) scanner.nextLine(); // skip header
                while (scanner.hasNextLine()) {
                    String line = scanner.nextLine();
                    String[] parts = line.split(",");
                    if (parts.length >= 5) {
                        LocalDate date = LocalDate.parse(parts[0]);
                        String type = parts[1];
                        String category = parts[2];
                        double amount = Double.parseDouble(parts[3]);
                        String description = parts[4];
                        Transaction t = new Transaction(amount, type, category, date, description);
                        transactions.add(t);
                        transactionModel.addRow(t.toTableRow());
                    }
                }
                JOptionPane.showMessageDialog(this, "CSV imported successfully.");
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Error importing CSV: " + ex.getMessage());
            }
        }
    }
    
    // A simple PDF export that writes plain text to a file with a .pdf extension.
    private void exportPDF() {
        JFileChooser fileChooser = new JFileChooser();
        if (fileChooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            try (FileWriter fw = new FileWriter(fileChooser.getSelectedFile())) {
                fw.write("Spending Report\n\n");
                for (Transaction t : transactions) {
                    fw.write(String.format("%s | %s | %s | $%.2f | %s\n", t.getDate(), t.getType(), t.getCategory(), t.getAmount(), t.toTableRow()[4]));
                }
                fw.flush();
                JOptionPane.showMessageDialog(this, "PDF exported successfully (as plain text).");
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(this, "Error exporting PDF: " + ex.getMessage());
            }
        }
    }
    
    private void saveData() {
        try (Connection conn = getConnection()) {
            conn.setAutoCommit(false);
            Statement stmt = conn.createStatement();
            
            // Clear existing records
            stmt.executeUpdate("DELETE FROM transactions");
            stmt.executeUpdate("DELETE FROM budgets");
            stmt.executeUpdate("DELETE FROM reminders");
            stmt.executeUpdate("DELETE FROM categories");
            
            // Save Transactions
            PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO transactions(date, type, category, amount, description) VALUES (?, ?, ?, ?, ?)");
            for (Transaction t : transactions) {
                ps.setString(1, t.getDate().toString());
                ps.setString(2, t.getType());
                ps.setString(3, t.getCategory());
                ps.setDouble(4, t.getAmount());
                // Assuming description is stored in the 5th column of toTableRow()
                ps.setString(5, t.toTableRow()[4].toString());
                ps.addBatch();
            }
            ps.executeBatch();
            
            // Save Budgets
            ps = conn.prepareStatement(
            	    "INSERT INTO budgets(category, limit_amount, spent) VALUES (?, ?, ?)");
            	for (Budget b : budgets.values()) {
            	    ps.setString(1, b.getCategory());
            	    ps.setDouble(2, b.getLimit());  // getLimit() now corresponds to limit_amount
            	    ps.setDouble(3, b.getSpent());
            	    ps.addBatch();
            	}
            	ps.executeBatch();

            
            // Save Reminders
            ps = conn.prepareStatement(
                "INSERT INTO reminders(dueDate, description, paid) VALUES (?, ?, ?)");
            for (Reminder r : reminders) {
                ps.setString(1, r.getDueDate().toString());
                ps.setString(2, r.getDescription());
                ps.setInt(3, r.isPaid() ? 1 : 0);
                ps.addBatch();
            }
            ps.executeBatch();
            
            // Save Categories
            ps = conn.prepareStatement(
                "INSERT INTO categories(category) VALUES (?)");
            for (String cat : categories) {
                ps.setString(1, cat);
                ps.addBatch();
            }
            ps.executeBatch();
            
            conn.commit();
            JOptionPane.showMessageDialog(this, "Data saved to database successfully.");
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(this, "Error saving data to database: " + ex.getMessage());
        }
    }
    
    private void loadData() {
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {
             
             // Load Transactions
             transactions.clear();
             transactionModel.setRowCount(0);
             ResultSet rs = stmt.executeQuery("SELECT date, type, category, amount, description FROM transactions");
             while (rs.next()) {
                 LocalDate date = LocalDate.parse(rs.getString("date"));
                 String type = rs.getString("type");
                 String category = rs.getString("category");
                 double amount = rs.getDouble("amount");
                 String description = rs.getString("description");
                 Transaction t = new Transaction(amount, type, category, date, description);
                 transactions.add(t);
                 transactionModel.addRow(t.toTableRow());
             }
             
             // Load Budgets
             budgets.clear();
             rs = stmt.executeQuery("SELECT category, limit_amount, spent FROM budgets");
             while (rs.next()) {
                 String category = rs.getString("category");
                 double limit = rs.getDouble("limit_amount");
                 double spent = rs.getDouble("spent");
                 Budget b = new Budget(category, limit);
                 if (spent > 0) {
                     b.addExpense(spent);
                 }
                 budgets.put(category, b);
             }

             
             // Load Reminders
             reminders.clear();
             reminderModel.setRowCount(0);
             rs = stmt.executeQuery("SELECT dueDate, description, paid FROM reminders");
             while (rs.next()) {
                 LocalDate dueDate = LocalDate.parse(rs.getString("dueDate"));
                 String description = rs.getString("description");
                 boolean paid = rs.getInt("paid") == 1;
                 Reminder r = new Reminder(dueDate, description);
                 if (paid) r.markPaid();
                 reminders.add(r);
                 reminderModel.addRow(r.toTableRow());
             }
             
             // Load Categories
             categories.clear();
             rs = stmt.executeQuery("SELECT category FROM categories");
             while (rs.next()) {
                 categories.add(rs.getString("category"));
             }
             
             JOptionPane.showMessageDialog(this, "Data loaded from database successfully.");
        } catch (SQLException ex) {
             JOptionPane.showMessageDialog(this, "Error loading data from database: " + ex.getMessage());
        }
    }
    
    private void refreshBudgetTable(DefaultTableModel budgetModel) {
        budgetModel.setRowCount(0);

        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:finance_tracker.db");
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT id, category, limit, spent, (limit - spent) AS remaining FROM budgets")) {
            
            while (rs.next()) {
                budgetModel.addRow(new Object[]{
                    rs.getInt("id"), 
                    rs.getString("category"),
                    rs.getDouble("limit"),
                    rs.getDouble("spent"),
                    rs.getDouble("remaining")
                });
            }
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(null, "Error loading budgets: " + ex.getMessage());
        }
    }
    
    private void setupDailyReminderCheck() {
        Timer timer = new Timer();
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                checkReminders();
            }
        }, 0, 1000 * 60 * 60 * 24); // Daily check
    }
    
    private void checkReminders() {
        LocalDate today = LocalDate.now();
        for (Reminder reminder : reminders) {
            if (!reminder.isPaid() && reminder.getDueDate().isBefore(today.plusDays(1))) {
                JOptionPane.showMessageDialog(this,
                    "Upcoming payment due: " + reminder.getDescription());
            }
        }
    }
    
    private void styleComponents() {
        UIManager.put("Button.background", new Color(70, 130, 180));
        UIManager.put("Button.foreground", Color.WHITE);
        UIManager.put("TabbedPane.background", Color.WHITE);
    }
    
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignored) {}
            new FinanceTracker().setVisible(true);
        });
    }
}

class ChartPanel extends JPanel {
    private Map<String, Double> categorySpending = new HashMap<>();
    
    public ChartPanel() {
        setPreferredSize(new Dimension(600, 400));
    }
    
    public void updateData(Map<String, Double> data) {
        this.categorySpending = data;
        repaint();
    }
    
    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        
        // Check if there is data to draw
        if (categorySpending.isEmpty()) {
            g.drawString("No data available", 10, 10);
            return;
        }
        
        double max = categorySpending.values().stream()
                        .mapToDouble(Double::doubleValue)
                        .max()
                        .orElse(1);
        int width = getWidth();
        int height = getHeight();
        
        // Prevent division by zero by ensuring the divisor is at least 1
        int divisor = categorySpending.size() * 2;
        if(divisor == 0) {
            divisor = 1;
        }
        int barWidth = width / divisor;
        
        int i = 0;
        for (Map.Entry<String, Double> entry : categorySpending.entrySet()) {
            int barHeight = (int) ((entry.getValue() / max) * (height - 50));
            int x = i * 2 * barWidth + barWidth;
            int y = height - barHeight - 30;
            g.setColor(Color.BLUE);
            g.fillRect(x, y, barWidth, barHeight);
            g.setColor(Color.BLACK);
            g.drawString(entry.getKey(), x, height - 10);
            i++;
        }
    }
}
