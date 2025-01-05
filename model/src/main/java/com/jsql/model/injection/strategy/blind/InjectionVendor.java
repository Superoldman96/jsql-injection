package com.jsql.model.injection.strategy.blind;

import com.jsql.model.InjectionModel;
import com.jsql.model.exception.StoppedByUserSlidingException;
import com.jsql.model.injection.strategy.blind.patch.Diff;
import com.jsql.model.injection.vendor.model.Vendor;
import com.jsql.model.injection.vendor.model.VendorYaml;
import com.jsql.util.LogLevelUtil;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

public class InjectionVendor {

    /**
     * Log4j logger sent to view.
     */
    private static final Logger LOGGER = LogManager.getRootLogger();

    // Source code of the FALSE web page (eg. ?id=-123456789)
    private String blankFalseMark;

    private List<Diff> constantTrueMark = new ArrayList<>();

    protected final InjectionModel injectionModel;

    private final List<String> falsy;

    public InjectionVendor(InjectionModel injectionModel, String vendorSpecificWithMode, Vendor vendor) {
        this.injectionModel = injectionModel;

        List<String> truthy = this.injectionModel.getMediatorVendor().getVendor().instance().getTruthy();
        this.falsy = this.injectionModel.getMediatorVendor().getVendor().instance().getFalsy();
        
        // No blind
        if (truthy.isEmpty() || this.injectionModel.isStoppedByUser()) {
            return;
        }
        
        // Call the SQL request which must be FALSE (usually ?id=-123456879)
        this.blankFalseMark = this.callUrl(
            StringUtils.EMPTY,
            "vendor:" + vendor
        );

        // Concurrent calls to the FALSE statements,
        // it will use inject() from the model
        ExecutorService taskExecutor = this.injectionModel.getMediatorUtils().getThreadUtil().getExecutor("CallableVendorTagTrue");
        Collection<CallableVendor> listCallableTagTrue = new ArrayList<>();
        for (String urlTest: truthy) {
            listCallableTagTrue.add(
                new CallableVendor(
                    vendorSpecificWithMode.replace(VendorYaml.TEST, urlTest),
                    this,
                    "vendor#true"
                )
            );
        }
        
        // Delete junk from the results of FALSE statements,
        // keep only opcodes found in each and every FALSE pages.
        // Allow the user to stop the loop
        try {
            List<Future<CallableVendor>> listTagTrue = taskExecutor.invokeAll(listCallableTagTrue);
            this.injectionModel.getMediatorUtils().getThreadUtil().shutdown(taskExecutor);
            
            for (var i = 1 ; i < listTagTrue.size() ; i++) {
                if (this.injectionModel.isStoppedByUser()) {
                    return;
                }
                if (this.constantTrueMark.isEmpty()) {
                    this.constantTrueMark = listTagTrue.get(i).get().getOpcodes();
                } else {
                    this.constantTrueMark.retainAll(listTagTrue.get(i).get().getOpcodes());
                }
            }
        } catch (ExecutionException e) {
            LOGGER.log(LogLevelUtil.CONSOLE_JAVA, e, e);
        } catch (InterruptedException e) {
            LOGGER.log(LogLevelUtil.IGNORE, e, e);
            Thread.currentThread().interrupt();
        }
        
        this.initializeFalseMarks(vendorSpecificWithMode);
    }
    
    private void initializeFalseMarks(String vendorSpecificWithMode) {
        // Concurrent calls to the TRUE statements,
        // it will use inject() from the model.
        ExecutorService taskExecutor = this.injectionModel.getMediatorUtils().getThreadUtil().getExecutor("CallableVendorTagFalse");
        Collection<CallableVendor> listCallableTagFalse = new ArrayList<>();
        
        for (String urlTest: this.falsy) {
            listCallableTagFalse.add(
                new CallableVendor(
                    vendorSpecificWithMode.replace(VendorYaml.TEST, urlTest),
                    this,
                    "vendor#false"
                )
            );
        }
        
        // Remove TRUE opcodes in the FALSE opcodes, because
        // a significant FALSE statement shouldn't contain any TRUE opcode.
        // Allow the user to stop the loop.
        try {
            List<Future<CallableVendor>> listTagFalse = taskExecutor.invokeAll(listCallableTagFalse);
            this.injectionModel.getMediatorUtils().getThreadUtil().shutdown(taskExecutor);
        
            for (Future<CallableVendor> falseTag: listTagFalse) {
                
                if (this.injectionModel.isStoppedByUser()) {
                    return;
                }

                this.constantTrueMark.removeAll(falseTag.get().getOpcodes());
            }
        } catch (ExecutionException e) {
            LOGGER.log(LogLevelUtil.CONSOLE_JAVA, e, e);
        } catch (InterruptedException e) {
            LOGGER.log(LogLevelUtil.IGNORE, e, e);
            Thread.currentThread().interrupt();
        }
    }

    public boolean isInjectable(String vendorSpecificWithMode) throws StoppedByUserSlidingException {
        if (this.injectionModel.isStoppedByUser()) {
            throw new StoppedByUserSlidingException();
        }

        var blindTest = new CallableVendor(
            vendorSpecificWithMode.replace(VendorYaml.TEST, this.injectionModel.getMediatorVendor().getVendor().instance().sqlTestBinaryInitialization()),
            this,
            "vendor#confirm"
        );
        try {
            blindTest.call();
        } catch (Exception e) {
            LOGGER.log(LogLevelUtil.CONSOLE_JAVA, e, e);
        }

        return blindTest.isTrue() && !this.constantTrueMark.isEmpty();
    }
    
    public String callUrl(String urlString, String metadataInjectionProcess) {
        return this.injectionModel.injectWithoutIndex(urlString, metadataInjectionProcess);
    }

    public String callUrl(String urlString, String metadataInjectionProcess, AbstractCallableBinary<?> callableBoolean) {
        return this.injectionModel.injectWithoutIndex(urlString, metadataInjectionProcess, callableBoolean);
    }


    // Getter

    public String getBlankFalseMark() {
        return this.blankFalseMark;
    }
    
    public List<Diff> getConstantTrueMark() {
        return this.constantTrueMark;
    }
}
