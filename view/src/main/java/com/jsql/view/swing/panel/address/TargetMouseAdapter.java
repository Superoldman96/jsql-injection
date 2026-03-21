package com.jsql.view.swing.panel.address;

import com.jsql.util.CookiesUtil;
import com.jsql.util.LogLevelUtil;
import com.jsql.util.ParameterUtil;
import com.jsql.view.swing.panel.PanelAddressBar;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.swing.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.AbstractMap;
import java.util.Arrays;
import java.util.regex.Pattern;
import java.util.stream.Stream;

class TargetMouseAdapter extends MouseAdapter {

    private static final Logger LOGGER = LogManager.getRootLogger();

    private final PanelTrailingAddress panelTrailingAddress;
    private final PanelAddressBar panelAddressBar;
    private final JPopupMenu popupMenuTargets = new JPopupMenu();

    public TargetMouseAdapter(PanelTrailingAddress panelTrailingAddress, PanelAddressBar panelAddressBar) {
        this.panelTrailingAddress = panelTrailingAddress;
        this.panelAddressBar = panelAddressBar;
    }

    @Override
    public void mousePressed(MouseEvent event) {
        this.popupMenuTargets.removeAll();
        JRadioButtonMenuItem menuParamAuto = new JRadioButtonMenuItem(PanelTrailingAddress.PARAM_AUTO);
        menuParamAuto.setActionCommand(PanelTrailingAddress.PARAM_AUTO);  // mock required when adding star: @ParameterUtil.controlInput
        menuParamAuto.addActionListener(actionEvent ->
            this.panelTrailingAddress.getLabelTarget().setText(menuParamAuto.getText())
        );
        this.popupMenuTargets.add(menuParamAuto);

        var rawQuery = this.panelAddressBar.getTextFieldAddress().getText().trim();
        var rawRequest = this.panelAddressBar.getTextFieldRequest().getText().trim();
        var rawHeader = this.panelAddressBar.getTextFieldHeader().getText().trim();

        var selection = this.panelTrailingAddress.getGroupRadio().getSelection();
        String selectionCommand;  // effectively final
        if (selection != null) {
            selectionCommand = selection.getActionCommand();
        } else {
            selectionCommand = StringUtils.EMPTY;
        }
        this.panelTrailingAddress.setGroupRadio(new ButtonGroup());
        this.panelTrailingAddress.getGroupRadio().add(menuParamAuto);
        JMenu menuQuery = new JMenu("Query");
        if (!rawQuery.isEmpty()) {
            try {
                rawQuery = !rawQuery.matches("(?i)^\\w+://.*") ? "http://" + rawQuery : rawQuery;
                var url = new URI(rawQuery).toURL();
                if (url.getQuery() != null) {
                    this.buildMenu(url.getQuery(), ParameterUtil.PREFIX_COMMAND_QUERY, selectionCommand, menuQuery);
                }
            } catch (IllegalArgumentException | MalformedURLException | URISyntaxException e) {
                LOGGER.log(LogLevelUtil.CONSOLE_ERROR, "Incorrect URL: {}", e.getMessage());
                return;
            }
        }

        JMenu menuRequest = new JMenu("Request");
        if (!rawRequest.isEmpty()) {
            this.buildMenu(rawRequest, ParameterUtil.PREFIX_COMMAND_REQUEST, selectionCommand, menuRequest);
        }

        JMenu menuHeader = new JMenu("Header");
        if (!rawHeader.isEmpty()) {
            this.buildMenuHeader(rawHeader, selectionCommand, menuHeader);
        }

        Arrays.stream(this.popupMenuTargets.getComponents())
            .map(JComponent.class::cast)
            .forEach(c -> c.setEnabled(false));
        menuParamAuto.setEnabled(true);
        if (this.panelTrailingAddress.getGroupRadio().getSelection() == null) {
            menuParamAuto.setSelected(true);
            this.panelTrailingAddress.getLabelTarget().setText(menuParamAuto.getText());
        }
        menuQuery.setEnabled(menuQuery.getMenuComponentCount() > 0);
        menuRequest.setEnabled(menuRequest.getMenuComponentCount() > 0);
        menuHeader.setEnabled(menuHeader.getMenuComponentCount() > 0);

        if (
            menuQuery.getMenuComponentCount() > 0
            || menuRequest.getMenuComponentCount() > 0
            || menuHeader.getMenuComponentCount() > 0
        ) {
            Arrays.stream(this.popupMenuTargets.getComponents())
                .map(JComponent.class::cast)
                .forEach(JComponent::updateUI);  // required: incorrect when dark/light mode switch
            this.popupMenuTargets.updateUI();  // required: incorrect when dark/light mode switch
            SwingUtilities.invokeLater(() -> {  // reduce flickering on linux
                this.popupMenuTargets.show(event.getComponent(), event.getComponent().getX(), 5 + event.getComponent().getY() + event.getComponent().getHeight());
                this.popupMenuTargets.setLocation(event.getComponent().getLocationOnScreen().x, 5 + event.getComponent().getLocationOnScreen().y + event.getComponent().getHeight());
            });
        } else {
            LOGGER.log(LogLevelUtil.CONSOLE_ERROR, "Missing parameter to inject");
        }
    }

    private void buildMenuHeader(String rawHeader, String selectionCommand, JMenu menuHeader) {
        var listHeaders = Pattern.compile("\\\\r\\\\n")
            .splitAsStream(rawHeader)
            .map(keyValue -> Arrays.copyOf(keyValue.split(":"), 2))
            .map(keyValue -> new AbstractMap.SimpleEntry<>(
                keyValue[0],
                keyValue[1] == null ? StringUtils.EMPTY : keyValue[1]
            ))
            .toList();
        listHeaders.forEach(entry -> {
            JRadioButtonMenuItem menuItem = new JRadioButtonMenuItem(entry.getKey());
            menuItem.setSelected((ParameterUtil.PREFIX_COMMAND_HEADER + entry.getKey()).equals(selectionCommand));
            menuItem.setActionCommand(ParameterUtil.PREFIX_COMMAND_HEADER + entry.getKey());
            menuItem.addActionListener(actionEvent ->
                this.panelTrailingAddress.getLabelTarget().setText(entry.getKey())
            );
            this.panelTrailingAddress.getGroupRadio().add(menuItem);
            menuHeader.add(menuItem);
        });
        if (listHeaders.stream().anyMatch(s -> CookiesUtil.COOKIE.equalsIgnoreCase(s.getKey()))) {
            var cookies = listHeaders.stream()
                .filter(s -> CookiesUtil.COOKIE.equalsIgnoreCase(s.getKey()))
                .findFirst()
                .orElse(new AbstractMap.SimpleEntry<>(CookiesUtil.COOKIE, ""));
            if (!cookies.getValue().trim().isEmpty()) {
                JMenu menuCookie = new JMenu(CookiesUtil.COOKIE);
                String[] cookieValues = StringUtils.split(cookies.getValue(), ";");
                Stream.of(cookieValues).forEach(cookie -> {
                    String[] cookieEntry = StringUtils.split(cookie, "=");
                    JRadioButtonMenuItem menuItem = new JRadioButtonMenuItem(cookieEntry[0].trim());
                    menuItem.setSelected((ParameterUtil.PREFIX_COMMAND_COOKIE + cookieEntry[0].trim()).equals(selectionCommand));
                    menuItem.setActionCommand(ParameterUtil.PREFIX_COMMAND_COOKIE + cookieEntry[0].trim());
                    menuItem.addActionListener(actionEvent ->
                            this.panelTrailingAddress.getLabelTarget().setText(cookieEntry[0].trim())
                    );
                    this.panelTrailingAddress.getGroupRadio().add(menuItem);
                    menuCookie.add(menuItem);
                });
                menuHeader.addSeparator();
                menuHeader.add(menuCookie);
            }
        }
        this.popupMenuTargets.add(menuHeader);
    }

    private void buildMenu(String rawParams, String prefixCommand, String selectionCommand, JMenu menu) {
        Pattern.compile("&").splitAsStream(rawParams)
            .map(keyValue -> Arrays.copyOf(keyValue.split("="), 2))
            .map(keyValue -> new AbstractMap.SimpleEntry<>(
                keyValue[0],
                keyValue[1] == null ? StringUtils.EMPTY : keyValue[1]
            ))
            .forEach(entry -> {
                JRadioButtonMenuItem menuItem = new JRadioButtonMenuItem(entry.getKey());
                menuItem.setSelected((prefixCommand + entry.getKey()).equals(selectionCommand));
                menuItem.setActionCommand(prefixCommand + entry.getKey());
                menuItem.addActionListener(actionEvent ->
                    this.panelTrailingAddress.getLabelTarget().setText(entry.getKey())
                );
                this.panelTrailingAddress.getGroupRadio().add(menuItem);
                menu.add(menuItem);
            });
        this.popupMenuTargets.add(menu);
    }
}
