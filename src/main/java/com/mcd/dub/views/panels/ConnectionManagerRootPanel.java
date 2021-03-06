package com.mcd.dub.views.panels;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.JBMenuItem;
import com.intellij.openapi.ui.JBPopupMenu;
import com.intellij.openapi.util.IconLoader;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBPanel;
import com.intellij.ui.components.JBTabbedPane;
import com.mcd.dub.intellij.task.ExecuteRunnableInBackGroundWithProgress;
import com.mcd.dub.intellij.utils.Constants.SqlDatabaseTypes;
import com.mcd.dub.intellij.utils.DbViewerPluginUtils;
import com.mcd.dub.views.models.StoredConnectionsModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;
import java.awt.*;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.sql.SQLException;
import java.util.List;

import static com.intellij.notification.NotificationType.INFORMATION;
import static com.intellij.openapi.progress.PerformInBackgroundOption.ALWAYS_BACKGROUND;
import static com.mcd.dub.intellij.utils.Constants.SqlDatabaseTypes.SQLITE;

public class ConnectionManagerRootPanel extends JBPanel<ConnectionManagerRootPanel> implements PropertyChangeListener {

    private static final Logger logger = LoggerFactory.getLogger(ConnectionManagerRootPanel.class);
    
    private final Project project;
    private final JBPopupMenu tabMenu = new JBPopupMenu();
    private final JBMenuItem closeConnectionPopupMenuItem = new JBMenuItem("Close Connection"),
                                buildDbViewPopupMenuItem = new JBMenuItem("Build View");
    
    private JPanel rootPanel, cardPanel;
    private JBTabbedPane outerTabbedPane, innerTabbedPane;
    private ConnectionSettingsPanel connectionSettingsPanel;
    private JSplitPane jsplitPane;
    private OpenConnectionsTable openConnectionsTable;
    private StoredConnectionsTable storedConnectionsTable;
    private ConnectionPoolStatusTable connectionPoolStatusTable;
    private JButton backButton;

    public ConnectionManagerRootPanel(Project project) {
        this.project = project;
        addListenersForThisPanel();
        tabMenu.add(closeConnectionPopupMenuItem);
        tabMenu.add(buildDbViewPopupMenuItem);
        outerTabbedPane.setComponentPopupMenu(tabMenu);
        jsplitPane.setDividerLocation(510);
        jsplitPane.setEnabled(false);
        connectionSettingsPanel.addPropertyChangeListener(this);
        connectionPoolStatusTable.addListeners(project);
    }

    private void addListenersForThisPanel() {
        closeConnectionPopupMenuItem.addActionListener(actionEvent -> {
            if (outerTabbedPane.getSelectedIndex() > 0) {
                ApplicationManager.getApplication().invokeLater(() -> {
                    ((DatabaseViewPanel) outerTabbedPane.getSelectedComponent()).destroyModel();
                    openConnectionsTable.removeFromDataMap(outerTabbedPane.getSelectedIndex() -1);
                    outerTabbedPane.removeTabAt(outerTabbedPane.getSelectedIndex());
                });
            }
            if(storedConnectionsTable.getAvailableConnections().isEmpty()) {
                connectionSettingsPanel.clearConnectionDetailsButton.doClick();
            }
        });

        tabMenu.addPopupMenuListener(new PopupMenuListener() {
            @Override
            public void popupMenuWillBecomeVisible(PopupMenuEvent popupMenuEvent) {
                ApplicationManager.getApplication().invokeLater(() -> {
                    if (outerTabbedPane.getSelectedIndex() != 0 && outerTabbedPane.getTabComponentAt(outerTabbedPane.getSelectedIndex()) != null) {
                        ApplicationManager.getApplication().invokeLater(() -> closeConnectionPopupMenuItem.setVisible(true));
                    } else {
                        ApplicationManager.getApplication().invokeLater(() -> {
                            tabMenu.setVisible(false);
                            closeConnectionPopupMenuItem.setVisible(false);
                        });
                    }
                });
            }
            @Override
            public void popupMenuWillBecomeInvisible(PopupMenuEvent popupMenuEvent) { }
            @Override
            public void popupMenuCanceled(PopupMenuEvent popupMenuEvent) { }
        });

        buildDbViewPopupMenuItem.addActionListener(actionEvent -> {
            //TODO - User View Tables
        });

        backButton.addActionListener(e -> ApplicationManager.getApplication().invokeLater(() -> ((CardLayout)cardPanel.getLayout()).show(cardPanel, "SettingsPanel")));
    }

    private void createUIComponents() {
        rootPanel = this;
        connectionPoolStatusTable = new ConnectionPoolStatusTable();
        storedConnectionsTable = new StoredConnectionsTable();
        openConnectionsTable = new OpenConnectionsTable();
        storedConnectionsTable.addPropertyChangeListener(this);
        openConnectionsTable.addPropertyChangeListener(this);
    }

    private void handleRequest(PropertyChangeEvent pce) {
        if(pce.getPropertyName().equals(ConnectionSettingsPanel.class.getSimpleName())) {
            switch (pce.getOldValue().toString()) {
                case "Ping":
                    break;
                case "Connect":
                    List<Object> dbConnectionSettings = connectionSettingsPanel.getDatabaseSettings();
                    createConnection(dbConnectionSettings, connectionSettingsPanel.userPassword.getPassword(), ((StoredConnectionsModel) storedConnectionsTable.getModel()).uniqueRowCheck(dbConnectionSettings));
                    storedConnectionsTable.alignTable();
                    connectionSettingsPanel.createConnectionButton.setEnabled(true);
                    break;
                case "Clear":
                    storedConnectionsTable.clearSelection();
                    connectionSettingsPanel.clearConnectionDetailsButton.setEnabled(true);
                    break;
                case "Status":
                    ((CardLayout)cardPanel.getLayout()).show(cardPanel, "StatusPanel");
                    connectionSettingsPanel.statusButton.setEnabled(true);
                    break;
                default:
                    //TODO - Log Unhandled Event
                    break;
            }
        } else if (pce.getPropertyName().equals(StoredConnectionsTable.class.getSimpleName())) {
            switch (pce.getOldValue().toString()) {
                case "getValueIsAdjusting":
                    final List<Object> rowData = ((StoredConnectionsModel) storedConnectionsTable.getModel()).getDataForRowByTabIndex((Integer) pce.getNewValue());
                    connectionSettingsPanel.setDatabaseSettings(rowData);
                    break;
                case "focusLost":
                    if (connectionSettingsPanel.createConnectionButton.hasFocus() ||
                            connectionSettingsPanel.clearConnectionDetailsButton.hasFocus() || connectionSettingsPanel.databaseName.hasFocus() ||
                            connectionSettingsPanel.resultingUrl.hasFocus() || connectionSettingsPanel.userName.hasFocus() ||
                            connectionSettingsPanel.userPassword.hasFocus() || connectionSettingsPanel.vendor.hasFocus()) {
                        connectionSettingsPanel.vendor.setBackground(JBColor.RED);
                    }
                    break;
                case "deleteStoredConnection":
                    ((StoredConnectionsModel) storedConnectionsTable.getModel()).removeRow((Integer) pce.getNewValue());
                    break;
                default:
                    //TODO - Log Unhandled Event
                    break;
            }
        } else if (pce.getPropertyName().equals(OpenConnectionsTable.class.getSimpleName())) {
            switch (pce.getOldValue().toString()) {
                case "closeConnectionFromTable":
                    ((DatabaseViewPanel) outerTabbedPane.getComponentAt((Integer) pce.getNewValue())).destroyModel();
                    ApplicationManager.getApplication().invokeLater(() -> outerTabbedPane.removeTabAt((Integer) pce.getNewValue()));
                    break;
                default:
                    //TODO - Log Unhandled Event
                    break;
            }
        } else {
            //TODO - Log Received Event from Unhandled Source Class.
        }

    }

    @Override
    public void propertyChange(PropertyChangeEvent pce) {
        ApplicationManager.getApplication().invokeLater(() -> handleRequest(pce));
    }

    private void createConnection(List<Object> databaseConnectionSettings, char[] dbPassword, boolean createNew) {
        if (connectionSettingsPanel.paramsPassValidation(project, databaseConnectionSettings)) {
            final DatabaseViewPanel[] newDbTableTab = new DatabaseViewPanel[1];
            try {
                newDbTableTab[0] = new DatabaseViewPanel(project, databaseConnectionSettings, dbPassword);
            } catch (SQLException | IllegalStateException ex) {
                logger.error("::createConnection -> ", ex);
                return;
            }

            new ExecuteRunnableInBackGroundWithProgress("Creating Database View...", false, false, project, ALWAYS_BACKGROUND, () ->
            {
                JBPanel<?> databaseViewPanel = newDbTableTab[0].createContent();
                SqlDatabaseTypes sqlDatabaseTypes = (SqlDatabaseTypes) connectionSettingsPanel.vendor.getSelectedItem();
                if(databaseViewPanel != null && sqlDatabaseTypes != null) {
                    ApplicationManager.getApplication().invokeLater(() -> {
                        final boolean isSqliteDb = sqlDatabaseTypes.equals(SQLITE);
                        final String tabName;
                        if(isSqliteDb) {
                            final String fullDbName = connectionSettingsPanel.databaseName.getText();
                            tabName = sqlDatabaseTypes.name() + "-" + fullDbName.substring(fullDbName.lastIndexOf('/') +1);
                        } else {
                            tabName = sqlDatabaseTypes.name() + "-" + connectionSettingsPanel.databaseName.getText();
                        }
                        databaseConnectionSettings.add(outerTabbedPane.getTabCount());
                        if(createNew) {
                            storedConnectionsTable.addToDataMap(databaseConnectionSettings);
                            if(!openConnectionsTable.addToDataMap(databaseConnectionSettings)) {
                                //TODO - Log Error.
                            }
                        } else {
                            if(!openConnectionsTable.addToDataMap(databaseConnectionSettings)){
                                //TODO - Log Error.
                            }
                            //TODO - If a connection to the same table exists append count.
                        }
                        outerTabbedPane.addTab(tabName, IconLoader.getIcon("/icons/Database.png"), databaseViewPanel , "");
                        outerTabbedPane.setSelectedIndex(outerTabbedPane.getTabCount() - 1);
                        connectionSettingsPanel.createConnectionButton.setEnabled(true);
                    });
                    DbViewerPluginUtils.INSTANCE.writeToEventLog(INFORMATION, "-> createConnection -> Successfully Created Tab for Database: " + connectionSettingsPanel.resultingUrl.getText(), null, false, true);
                }
            }).queue();
        }
    }

}
