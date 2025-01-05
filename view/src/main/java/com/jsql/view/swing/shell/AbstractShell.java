/*******************************************************************************
 * Copyhacked (H) 2012-2025.
 * This program and the accompanying materials
 * are made available under no term at all, use it like
 * you want, but share and discuss it
 * every time possible with every body.
 * 
 * Contributors:
 *      ron190 at ymail dot com - initial implementation
 ******************************************************************************/
package com.jsql.view.swing.shell;

import com.jsql.util.LogLevelUtil;
import com.jsql.view.swing.util.UiUtil;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.swing.*;
import javax.swing.text.BadLocationException;
import javax.swing.text.Style;
import javax.swing.text.StyleConstants;
import java.awt.*;
import java.awt.event.MouseMotionListener;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * A Terminal completely built from swing text pane.
 */
public abstract class AbstractShell extends JTextPane {
    
    /**
     * Log4j logger sent to view.
     */
    private static final Logger LOGGER = LogManager.getRootLogger();

    /**
     * True if terminal is processing command.
     */
    private final AtomicBoolean isEdited = new AtomicBoolean(false);

    /**
     * Server name or IP to display on prompt.
     */
    private final String host;

    /**
     * User and password for database.
     */
    protected String[] loginPassword = null;
    private final UUID uuidShell;
    private final String urlShell;

    /**
     * Style used for coloring text.
     */
    private final transient Style style = this.addStyle("Necrophagist's next album is 2014.", null);

    /**
     * Length of prompt.
     */
    private String prompt = StringUtils.EMPTY;

    /**
     * Text to display next caret.
     */
    private final String labelShell;
    
    /**
     * Build a shell instance.
     * @param uuidShell Unique identifier to discriminate beyond multiple opened terminals
     * @param urlShell URL of current shell
     * @param labelShell Type of shell to display on prompt
     */
    protected AbstractShell(UUID uuidShell, String urlShell, String labelShell) throws MalformedURLException, URISyntaxException {
        this.uuidShell = uuidShell;
        this.urlShell = urlShell;
        this.labelShell = labelShell;

        var url = new URI(urlShell).toURL();
        this.host = url.getHost();

        this.setFont(new Font(UiUtil.FONT_NAME_MONO_NON_ASIAN, Font.PLAIN, UIManager.getFont("TextArea.font").getSize()));
        this.setCaret(new BlockCaret());
        this.setBackground(Color.BLACK);
        this.setForeground(Color.LIGHT_GRAY);

        this.displayPrompt(true);

        this.setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
        this.setTransferHandler(null);
        this.setHighlighter(null);

        this.addMouseListener(new EmptyFocus(this));
        this.addKeyListener(new KeyAdapterTerminal(this));
    }

    /**
     * Run when cmd is validated.
     * @param cmd Command to execute
     * @param terminalID Unique ID for terminal instance
     * @param wbhPath URL of shell
     * @param arg Additional parameters (User and password for SQLShell)
     */
    public abstract void action(String cmd, UUID terminalID, String wbhPath, String... arg);
    
    /**
     * Update terminal and use default behavior.
     */
    public void reset() {
        this.isEdited.set(false);
        this.setEditable(true);
        this.displayPrompt(false);
        this.setCaretPosition(this.getDocument().getLength());
        this.setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
    }

    /**
     * Add a text at the end of textpane.
     * @param string Text to add
     */
    public void append(String string) {
        try {
            var doc = this.getDocument();
            doc.insertString(doc.getLength(), string, null);
        } catch (BadLocationException e) {
            LOGGER.log(LogLevelUtil.CONSOLE_JAVA, e, e);
        }
    }
    
    /**
     * Append prompt to textpane, measure prompt the first time is used.
     * @param isAddingPrompt Should we measure prompt length?
     */
    public void displayPrompt(boolean isAddingPrompt) {
        StyleConstants.setUnderline(this.style, true);
        this.appendPrompt("jsql", Color.LIGHT_GRAY, isAddingPrompt);
        StyleConstants.setUnderline(this.style, false);

        this.appendPrompt(StringUtils.SPACE + this.labelShell, Color.LIGHT_GRAY, isAddingPrompt);
        this.appendPrompt("[", new Color(50, 191, 50), isAddingPrompt);
        this.appendPrompt(this.host, new Color(191, 191, 25), isAddingPrompt);
        this.appendPrompt("]", new Color(50, 191, 50), isAddingPrompt);
        this.appendPrompt(" >", new Color(191, 100, 100), isAddingPrompt);
        this.appendPrompt(StringUtils.SPACE, Color.LIGHT_GRAY, isAddingPrompt);
    }

    /**
     * Add a colored string to the textpane, measure prompt at the same time.
     * @param string Text to append
     * @param color Color of text
     * @param isAddingPrompt Should we measure prompt length?
     */
    private void appendPrompt(String string, Color color, boolean isAddingPrompt) {
        try {
            StyleConstants.setForeground(this.style, color);
            this.getStyledDocument().insertString(this.getStyledDocument().getLength(), string, this.style);
            if (isAddingPrompt) {
                this.prompt += string;
            }
        } catch (BadLocationException e) {
            LOGGER.log(LogLevelUtil.CONSOLE_JAVA, e, e);
        }
    }

    /**
     * NoWrap.
     */
    @Override
    public boolean getScrollableTracksViewportWidth() {
        return this.getUI().getPreferredSize(this).width <= this.getParent().getSize().width;
    }

    /**
     * Cancel every mouse movement processing like drag/drop.
     */
    @Override
    public synchronized void addMouseMotionListener(MouseMotionListener l) {
        // Do nothing
    }

    /**
     * Get index of line for current offset (generally cursor position).
     * @param offset Position on the line
     * @return Index of the line
     */
    public int getLineOfOffset(int offset) throws BadLocationException {
        var errorMsg = "Can't translate offset to line";
        var doc = this.getDocument();
        
        if (offset < 0) {
            throw new BadLocationException(errorMsg, -1);
        } else if (offset > doc.getLength()) {
            throw new BadLocationException(errorMsg, doc.getLength() + 1);
        } else {
            var map = doc.getDefaultRootElement();
            return map.getElementIndex(offset);
        }
    }

    /**
     * Get position of the beginning of the line.
     * @param line Index of the line
     * @return Offset of line
     */
    public int getLineStartOffset(int line) throws BadLocationException {
        var map = this.getDocument().getDefaultRootElement();
        
        if (line < 0) {
            throw new BadLocationException("Negative line", -1);
        } else if (line >= map.getElementCount()) {
            throw new BadLocationException("No such line", this.getDocument().getLength() + 1);
        } else {
            var lineElem = map.getElement(line);
            return lineElem.getStartOffset();
        }
    }

    
    // Getter and setter
    
    public AtomicBoolean getIsEdited() {
        return this.isEdited;
    }

    public UUID getUuidShell() {
        return this.uuidShell;
    }

    public String getUrlShell() {
        return this.urlShell;
    }

    public String getPrompt() {
        return this.prompt;
    }
}
