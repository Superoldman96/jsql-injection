package com.jsql.model.injection.strategy;

import com.jsql.model.InjectionModel;
import com.jsql.model.accessible.DataAccess;
import com.jsql.model.bean.util.Interaction;
import com.jsql.model.bean.util.Request;
import com.jsql.model.exception.JSqlException;
import com.jsql.model.injection.vendor.model.VendorYaml;
import com.jsql.model.suspendable.AbstractSuspendable;
import com.jsql.model.suspendable.SuspendableGetIndexes;
import com.jsql.util.I18nUtil;
import com.jsql.util.LogLevelUtil;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;

public class StrategyInjectionNormal extends AbstractStrategy {
    
    /**
     * Log4j logger sent to view.
     */
    private static final Logger LOGGER = LogManager.getRootLogger();

    private String performanceLength = "0";
    
    public StrategyInjectionNormal(InjectionModel injectionModel) {
        super(injectionModel);
    }

    @Override
    public void checkApplicability() throws JSqlException {

        if (this.injectionModel.getMediatorUtils().getPreferencesUtil().isStrategyNormalDisabled()) {

            LOGGER.log(LogLevelUtil.CONSOLE_INFORM, AbstractStrategy.FORMAT_SKIP_STRATEGY_DISABLED, getName());
            return;
        }

        LOGGER.log(LogLevelUtil.CONSOLE_DEFAULT, "{} {}...", () -> I18nUtil.valueByKey("LOG_CHECKING_STRATEGY"), this::getName);
        this.injectionModel.setIndexesInUrl(new SuspendableGetIndexes(this.injectionModel).run());

        // Define visibleIndex, i.e, 2 in "[..]union select 1,2,[..]", if 2 is found in HTML body
        if (StringUtils.isNotEmpty(this.injectionModel.getIndexesInUrl())) {
            this.visibleIndex = this.getVisibleIndex(this.sourceIndexesFound);
        }
        
        this.isApplicable = StringUtils.isNotEmpty(this.injectionModel.getIndexesInUrl())
            && Integer.parseInt(this.injectionModel.getMediatorStrategy().getNormal().getPerformanceLength()) > 0
            && StringUtils.isNotBlank(this.visibleIndex);
        
        if (this.isApplicable) {
            
            LOGGER.log(
                LogLevelUtil.CONSOLE_SUCCESS,
                "{} [{}] at index [{}] using [{}] characters",
                () -> I18nUtil.valueByKey("LOG_VULNERABLE"),
                this::getName,
                () -> this.visibleIndex,
                () -> this.performanceLength
            );
            this.allow();
            
        } else {
            this.unallow();
        }
    }

    @Override
    public void allow(int... i) {

        this.injectionModel.appendAnalysisReport(
            "<span style=color:rgb(0,0,255)>### Strategy: " + getName() + "</span>"
            + this.injectionModel.getReportWithIndexes(
                this.injectionModel.getMediatorVendor().getVendor().instance().sqlNormal("<span style=color:rgb(0,128,0)>&lt;query&gt;</span>", "0", true),
                "metadataInjectionProcess"
            )
        );
        this.markVulnerability(Interaction.MARK_NORMAL_VULNERABLE);
    }

    @Override
    public void unallow(int... i) {
        this.markVulnerability(Interaction.MARK_NORMAL_INVULNERABLE);
    }

    @Override
    public String inject(String sqlQuery, String startPosition, AbstractSuspendable stoppable, String metadataInjectionProcess) {
        return this.injectionModel.injectWithIndexes(
            this.injectionModel.getMediatorVendor().getVendor().instance().sqlNormal(sqlQuery, startPosition, false),
            metadataInjectionProcess
        );
    }

    @Override
    public void activateWhenApplicable() {

        if (this.injectionModel.getMediatorStrategy().getStrategy() == null && this.isApplicable()) {

            LOGGER.log(
                LogLevelUtil.CONSOLE_INFORM,
                "{} [{}]",
                () -> I18nUtil.valueByKey("LOG_USING_STRATEGY"),
                this::getName
            );
            this.injectionModel.getMediatorStrategy().setStrategy(this.injectionModel.getMediatorStrategy().getNormal());

            var request = new Request();
            request.setMessage(Interaction.MARK_NORMAL_STRATEGY);
            this.injectionModel.sendToViews(request);
        }
    }
    
    /**
     * Runnable class, search the most efficient index.<br>
     * Some indexes will display a lots of characters, others won't,
     * so sort them by order of efficiency:<br>
     * find the one that displays the most number of characters.
     * @return Integer index with most efficiency and visible in source code
     */
    public String getVisibleIndex(String firstSuccessPageSource) {
        
        // Parse all indexes found
        // Fix #4007 (initialize firstSuccessPageSource to empty String instead of null)
        String regexAllIndexes = String.format(VendorYaml.FORMAT_INDEX, "(\\d+?)");
        var regexSearch = Pattern.compile("(?s)"+ regexAllIndexes).matcher(firstSuccessPageSource);
        
        List<String> foundIndexes = new ArrayList<>();
        while (regexSearch.find()) {
            foundIndexes.add(regexSearch.group(1));
        }

        String[] indexes = foundIndexes.toArray(new String[0]);

        // Make url shorter, replace useless indexes from 1337[index]7331 to 1
        String regexAllExceptIndexesFound = String.format(
            VendorYaml.FORMAT_INDEX,
            "(?!"+ String.join("|", indexes) +"7331)\\d*"
        );
        String indexesInUrl = this.injectionModel.getIndexesInUrl().replaceAll(regexAllExceptIndexesFound, "1");

        // Replace correct indexes from 1337(index)7331 to
        // ==> ${lead}(index)######...######
        // Search for index that displays the most #
        String performanceQuery = this.injectionModel.getMediatorVendor().getVendor().instance().sqlCapacity(indexes);
        String performanceSourcePage = this.injectionModel.injectWithoutIndex(performanceQuery, "normal#size");

        // Build a 2D array of string with:
        //     column 1: index
        //     column 2: # found, so #######...#######
        regexSearch = Pattern.compile("(?s)"+ DataAccess.LEAD +"(\\d+)(#+)").matcher(performanceSourcePage);
        
        List<String[]> performanceResults = new ArrayList<>();
        
        while (regexSearch.find()) {
            performanceResults.add(new String[]{regexSearch.group(1), regexSearch.group(2)});
        }

        if (performanceResults.isEmpty()) {
            
            this.performanceLength = "0";
            return null;
        }
        
        // Switch from previous array to 2D integer array
        //     column 1: length of #######...#######
        //     column 2: index
        var lengthFields = new Integer[performanceResults.size()][2];
        
        for (var i = 0; i < performanceResults.size(); i++) {
            lengthFields[i] = new Integer[] {
                    
                performanceResults.get(i)[1].length() + performanceResults.get(i)[0].length(),
                Integer.parseInt(performanceResults.get(i)[0])
            };
        }

        // Sort by length of #######...#######
        Arrays.sort(lengthFields, Comparator.comparing((Integer[] s) -> s[0]));
        Integer[] bestLengthFields = lengthFields[lengthFields.length - 1];
        this.performanceLength = bestLengthFields[0].toString();

        // Reduce all others indexes
        String regexAllIndexesExceptBest = String.format(
            VendorYaml.FORMAT_INDEX,
            "(?!"+ bestLengthFields[1] +"7331)\\d*"
        );
        indexesInUrl = indexesInUrl.replaceAll(regexAllIndexesExceptBest, "1");
        
        this.injectionModel.setIndexesInUrl(indexesInUrl);
        
        return Integer.toString(bestLengthFields[1]);
    }
    
    
    // Getters and setters
    
    @Override
    public String getPerformanceLength() {
        return this.performanceLength;
    }
    
    @Override
    public String getName() {
        return "Normal";
    }
}
