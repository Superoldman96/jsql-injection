package com.jsql.model.injection.strategy.blind.callable;

import com.jsql.model.injection.strategy.blind.InjectionCharInsertion;
import name.fraser.neil.plaintext.diff_match_patch;
import static name.fraser.neil.plaintext.diff_match_patch.Diff;

import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Define a call HTTP to the server, require the associated url, character
 * position and bit. Opcodes represent the differences between
 * the reference page, and the resulting page.
 */
public class CallableCharInsertion extends AbstractCallableBit<CallableCharInsertion> {
    
    private LinkedList<Diff> opcodes = new LinkedList<>();  // List of differences found between the reference page, and the present page

    private static final diff_match_patch DIFF_MATCH_PATCH = new diff_match_patch();

    private final InjectionCharInsertion injectionCharInsertion;
    
    private final String metadataInjectionProcess;
    
    /**
     * Constructor for preparation and blind confirmation.
     */
    public CallableCharInsertion(String inj, InjectionCharInsertion injectionCharInsertion, String metadataInjectionProcess) {
        this.injectionCharInsertion = injectionCharInsertion;
        this.metadataInjectionProcess = metadataInjectionProcess;
        this.booleanUrl = inj;
    }

    /**
     * Check if a result page means the SQL query is true,
     * confirm that nothing in the resulting page is also defined
     * in the pages from every FALSE SQL queries.
     * @return true if the current SQL query is true
     */
    @Override
    public boolean isTrue() {
        // Fix #95422: ConcurrentModificationException on iterator.next()
        List<Diff> copyTrueMarks = new CopyOnWriteArrayList<>(this.injectionCharInsertion.getConstantTrueMark());
        for (Diff trueDiff: copyTrueMarks) {
            if (!this.opcodes.contains(trueDiff)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Process the URL HTTP call, use function inject() from the model.
     * Build the list of differences found between TRUE and the current page.
     * @return Functional Blind Callable
     */
    @Override
    public CallableCharInsertion call() {
        String source = this.injectionCharInsertion.callUrl(this.booleanUrl, this.metadataInjectionProcess, this);
        
        this.opcodes = CallableCharInsertion.DIFF_MATCH_PATCH.diff_main(
            this.injectionCharInsertion.getBlankFalseMark(),
            source,
            false
        );

        CallableCharInsertion.DIFF_MATCH_PATCH.diff_cleanupEfficiency(this.opcodes);
        return this;
    }
    
    public List<Diff> getOpcodes() {
        return this.opcodes;
    }
}
