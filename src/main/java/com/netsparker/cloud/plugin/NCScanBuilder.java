package com.netsparker.cloud.plugin;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashMap;

import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardListBoxModel;
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import com.netsparker.cloud.model.IgnoredVulnerabilityStateFilters;
import com.netsparker.cloud.model.ReportType;
import com.netsparker.cloud.model.ScanCancelRequest;
import com.netsparker.cloud.model.ScanCancelRequestResult;
import com.netsparker.cloud.model.ScanInfoRequest;
import com.netsparker.cloud.model.ScanInfoRequestResult;
import com.netsparker.cloud.model.ScanRequest;
import com.netsparker.cloud.model.ScanRequestResult;
import com.netsparker.cloud.model.ScanTaskState;
import com.netsparker.cloud.model.ScanType;
import com.netsparker.cloud.model.VCSCommit;
import com.netsparker.cloud.model.WebsiteModel;
import com.netsparker.cloud.model.WebsiteModelRequest;
import com.netsparker.cloud.model.WebsiteProfileModel;
import com.netsparker.cloud.utility.AppCommon;

import org.apache.commons.lang.StringUtils;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.jenkinsci.Symbol;
import org.json.simple.parser.ParseException;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.bind.JavaScriptMethod;
import org.kohsuke.stapler.verb.POST;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.AbstractProject;
import hudson.model.Item;
import hudson.model.ParameterValue;
import hudson.model.ParametersAction;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import hudson.util.Secret;
import hudson.util.ListBoxModel.Option;
import jenkins.model.Jenkins;
import jenkins.tasks.SimpleBuildStep;
import net.sf.json.JSONObject;

public class NCScanBuilder extends Builder implements SimpleBuildStep {

    private String ncScanType;
    private String ncWebsiteId;
    private String ncProfileId;
    private Secret ncApiToken;
    private String ncServerURL;
    private String credentialsId;
    private String ncSeverity;
    private Boolean ncStopScan;
    private Boolean ncConfirmed;
    private Boolean ncDoNotFail;
    private Boolean ncIgnoreFalsePositive;
    private Boolean ncIgnoreRiskAccepted;
    private IgnoredVulnerabilityStateFilters ncFilters = new IgnoredVulnerabilityStateFilters();
    private String ncReportType;

    private final String apiTokenBuildParameterName = "APITOKEN";

    // Fields in config.jelly must match the parameter names in the
    // "DataBoundConstructor"
    // this ctor called when project's settings save method called
    @DataBoundConstructor
    public NCScanBuilder(String ncScanType, String ncWebsiteId, String ncProfileId, Boolean ncDoNotFail, String ncReportType) {
        this.ncScanType = ncScanType == null ? "" : ncScanType;
        this.ncWebsiteId = ncWebsiteId == null ? "" : ncWebsiteId;
        this.ncProfileId = ncProfileId == null ? "" : ncProfileId;
        this.ncDoNotFail = ncDoNotFail;
        this.ncReportType = ncReportType == null || ncReportType.equals("null") ? "ExecutiveSummary" : ncReportType;
    }

    public String getNcSeverity() {
        return ncSeverity;
    }

    public Boolean getNcStopScan() {
        return ncStopScan;
    }

    public Boolean getNcConfirmed(){
        return ncConfirmed;
    }

    public Boolean getNcDoNotFail(){
        return ncDoNotFail;
    }

    public String getNcScanType() {
        return ncScanType;
    }

    public String getCredentialsId() {
        return credentialsId;
    }
    
    public IgnoredVulnerabilityStateFilters getFilters(){
        return ncFilters;
    }

    public void setFilters(){
        this.ncFilters = new IgnoredVulnerabilityStateFilters();
        this.ncFilters.setFalsePositive(this.ncIgnoreFalsePositive);
        this.ncFilters.setAcceptedRisk(this.ncIgnoreRiskAccepted);
    }

    @DataBoundSetter
    public void setCredentialsId(String credentialsId) {
        this.credentialsId = credentialsId;
    }

    public void setNcScanType(String ncScanType) {
        this.ncScanType = ncScanType;
    }

    public String getNcWebsiteId() {
        return ncWebsiteId;
    }

    public void setNcWebsiteId(String ncTargetURL) {
        this.ncWebsiteId = ncTargetURL;
    }

    public String getNcProfileId() {
        return ncProfileId;
    }

    public void setNcProfileId(String ncProfileId) {
        this.ncProfileId = ncProfileId;
    }

    public String getNcServerURL() {
        return ncServerURL;
    }

    @DataBoundSetter
    public void setNcSeverity(String ncSeverity) {
        this.ncSeverity = ncSeverity;
    }

    @DataBoundSetter
    public void setNcStopScan(Boolean ncStopScan) {
        this.ncStopScan = ncStopScan;
    }

    @DataBoundSetter
    public void setNcConfirmed(Boolean ncConfirmed){
        this.ncConfirmed = ncConfirmed;
    }

    @DataBoundSetter
    public void setNcDoNotFail(Boolean ncDoNotFail){
        this.ncDoNotFail = ncDoNotFail;
    }

    @DataBoundSetter
    public void setNcServerURL(String ncServerURL) {
        this.ncServerURL = ncServerURL;
    }

    public Secret getNcApiToken() {
        if (ncApiToken == null) {
            ncApiToken = getDescriptor().getNcApiToken();
        }
        return ncApiToken;
    }

    @DataBoundSetter
    public void setNcApiToken(Object ncApiToken) {
        if (ncApiToken.getClass() == String.class) {
            this.ncApiToken = Secret.fromString((String) ncApiToken);
        }
        if (ncApiToken.getClass() == Secret.class) {
            this.ncApiToken = (Secret) ncApiToken;
        }
    }

    public Boolean getNcIgnoreFalsePositive() {
        return ncIgnoreFalsePositive;
    }

    @DataBoundSetter
    public void setNcIgnoreFalsePositive(Boolean ncIgnoreFalsePositive) {
        this.ncIgnoreFalsePositive = ncIgnoreFalsePositive;
    }

    public Boolean getNcIgnoreRiskAccepted() {
        return ncIgnoreRiskAccepted;
    }

    @DataBoundSetter
    public void setNcIgnoreRiskAccepted(Boolean ncIgnoreRiskAccepted) {
        this.ncIgnoreRiskAccepted = ncIgnoreRiskAccepted;
    }

    public String getNcReportType(){
        return ncReportType;
    }

    @DataBoundSetter
    public void setNcReportType(String ncReportType) {
        this.ncReportType = ncReportType;
    }

    @Override
    public void perform(Run<?, ?> build, FilePath workspace, Launcher launcher,
            TaskListener listener) throws InterruptedException, IOException {
        logInfo("Scan step created...", listener);

        NCScanSCMAction scmAction = build.getAction(NCScanSCMAction.class);
        VCSCommit commit = scmAction == null ? VCSCommit.empty(build) : scmAction.getVcsCommit();

        try {
            ScanRequestHandler(build, commit, listener);
        } catch (Exception e) {
            try {
                build.replaceAction(new NCScanResultAction(
                        ScanRequestResult.errorResult("Scan Request Failed:: " + e.getMessage())));
            } catch (Exception ex) {
                build.addAction(new NCScanResultAction(
                        ScanRequestResult.errorResult("Scan Request Failed:: " + e.getMessage())));
            }

            build.setResult(hudson.model.Result.FAILURE);
        }
    }

    private Secret GetApiTokenFromBuildParameters(Run<?, ?> build) {
        Secret secret = null;

        ParametersAction parametersAction = build.getAction(ParametersAction.class);
        if (parametersAction != null) {

            ParameterValue parameter = parametersAction.getAllParameters().stream()
                    .filter(p -> p.getName().contains(apiTokenBuildParameterName)).findAny()
                    .orElse(null);

            if (parameter != null && parameter.getValue() != null) {
                Object value = parameter.getValue();
                if (value != null && value.getClass() != null) {
                    if (value.getClass() == Secret.class) {
                        secret = (Secret) value;
                    } else if (value.getClass() == String.class) {
                        secret = Secret.fromString((String) value);
                    }
                }
            }
        }

        return secret;
    }

    private void ScanRequestHandler(Run<?, ?> build, VCSCommit commit, TaskListener listener)
            throws Exception {
        DescriptorImpl descriptor = getDescriptor();
        String ncServerURL = StringUtils.isBlank(getNcServerURL()) ? descriptor.getNcServerURL()
                : getNcServerURL();

        Secret ncApiToken = null;

        // jenkin's server url
        String rootUrl = null;

        if (!StringUtils.isEmpty(credentialsId)) {

            // build.getEnvironment().get("job_name")
            // "Folder 1/Folder 1 - Folder 1/Folder 1 - Folder 1 - Folder 1/Child Project"
            final StandardUsernamePasswordCredentials credential = AppCommon.findCredentialsById(
                    credentialsId, build.getEnvironment(listener).get("job_name"));

            if (credential != null) {
                ncServerURL = credential.getUsername();
                ncApiToken = credential.getPassword();
            }
        }

        // if token is not set, try to get from global variable or selected credential
        // from settings
        if (ncApiToken == null || ncApiToken.getPlainText().isEmpty()) {
            ncApiToken =
                    getNcApiToken() != null && StringUtils.isBlank(getNcApiToken().getPlainText())
                            ? descriptor.getNcApiToken()
                            : getNcApiToken();


            if (Secret.toString(ncApiToken) == ("$" + apiTokenBuildParameterName)) {
                ncApiToken = GetApiTokenFromBuildParameters(build);
            }
        }

        // StringUtils.isEmpty checks null or empty
        if (StringUtils.isEmpty(rootUrl)) {
            rootUrl = descriptor.getRootURL();
        }

        commit.setRootURL(rootUrl);

        ScanRequest scanRequest = new ScanRequest(ncServerURL, ncApiToken, ncScanType, ncWebsiteId,
                ncProfileId, commit);

        logInfo("Requesting scan...", listener);
        ClassicHttpResponse scanRequestResponse = scanRequest.scanRequest();
        logInfo("Response status code: " + scanRequestResponse.getCode(),
                listener);

        ScanRequestResult scanRequestResult =
                new ScanRequestResult(scanRequestResponse, ncServerURL, ncApiToken, ncReportType);
        build.replaceAction(new NCScanResultAction(scanRequestResult));

        setFilters();

        // HTTP status code 201 refers to created. This means our request added to
        // queue. Otherwise it is failed.
        if (scanRequestResult.getHttpStatusCode() == 201 && !scanRequestResult.isError()) {
            ScanRequestSuccessHandler(ncServerURL, ncApiToken, scanRequestResult,
                    scanRequestResult.getScanTaskId(), ncDoNotFail, ncConfirmed, 
                    ncFilters, listener);
        } else {
            ScanRequestFailureHandler(scanRequestResult, listener);
        }
    }

    private void ScanRequestSuccessHandler(String ncServerURL, Secret ncApiToken,
            ScanRequestResult scanRequestResult, String scanTaskId, Boolean doNotFail, Boolean isConfirmed, 
            IgnoredVulnerabilityStateFilters filters, TaskListener listener)
            throws IOException, URISyntaxException, InterruptedException {
        logInfo("Scan requested successfully.", listener);
        ScanTaskState scanStatus = ScanTaskState.Queued;
        Boolean scanAbortedExternally = false;
        Boolean scanInfoConnectionError = false;
        Boolean isScanStarted = false;
        Boolean isSeverityBreaked = false;

        try {
            while (!scanStatus.equals(ScanTaskState.Complete)) {
                ScanInfoRequest scanInfoRequest =
                        new ScanInfoRequest(ncServerURL, ncApiToken, scanTaskId, ncDoNotFail, ncConfirmed, ncFilters);

                logInfo("Requesting scan info...", listener);
                ClassicHttpResponse scanInfoRequestResponse = scanInfoRequest.scanInfoRequest();
                logInfo("Response scan info status code: "
                        + scanInfoRequestResponse.getCode(), listener);

                ScanInfoRequestResult scanInfoRequestResult = new ScanInfoRequestResult(scanInfoRequestResponse);

                if (scanInfoRequestResult.isError()) {
                    scanInfoConnectionError = true;
                    logInfo("Get scan info error", listener);
                    logError(scanInfoRequestResult.getErrorMessage(), listener);
                    throw new hudson.AbortException("Error when getting scan info!");
                }

                scanStatus = scanInfoRequestResult.getScanTaskState();

                if (scanInfoRequestResult.checkSeverity(ncSeverity)) {
                    isSeverityBreaked = true;
                    String severityText = SeverityOptionsForBuildFailMesssage().get(ncSeverity);
                    String failMessage =
                            "Build failed because scan contains " + severityText + " severity!";
                    logInfo(failMessage, listener);
                    throw new hudson.AbortException(failMessage);
                }
                
                if (scanStatus.equals(ScanTaskState.Scanning) && !isScanStarted) {
                    isScanStarted = true;
                    logInfo("Scan started...", listener);
                } else if (scanStatus.equals(ScanTaskState.Failed)
                        || scanStatus.equals(ScanTaskState.Cancelled)
                        || scanStatus.equals(ScanTaskState.Paused)
                        || scanStatus.equals(ScanTaskState.Pausing)) {
                    scanAbortedExternally = true;
                    logInfo("Scan aborted because state is " + scanStatus.toString(), listener);
                    throw new hudson.AbortException("The scan was aborted outside of this instance");
                }

                Thread.sleep(10000);
            }
            logInfo("Scan completed...", listener);
        } catch (hudson.AbortException e) {
            Boolean isCancel = (scanInfoConnectionError || ((ncStopScan != null && ncStopScan)
                && (isSeverityBreaked || !scanAbortedExternally)));
            if (isCancel){
                CancelScan(ncServerURL, ncApiToken, scanTaskId, listener);
            }
            throw new hudson.AbortException("The build was aborted");
        } catch (Exception e) {
            StringWriter errors = new StringWriter();
            e.printStackTrace(new PrintWriter(errors));
            logInfo(errors.toString(), listener);
            throw new hudson.AbortException("The build was aborted");
        }
    }

    private HashMap<String, String> SeverityOptionsForBuildFailMesssage() {
        HashMap<String, String> options = new HashMap<String, String>();
        options.put("DoNotFail", "Do not fail the build");
        options.put("Critical", "Critical");
        options.put("Critical,High", "High or above");
        options.put("Critical,High,Medium", "Medium or above");
        options.put("Critical,High,Medium,Low", "Low or above");
        options.put("Critical,High,Medium,Low,Best Practice", "Best Practices or above");
        return options;
    }

    private void CancelScan(String ncServerURL, Secret ncApiToken, String scanTaskId,
            TaskListener listener)
            throws IOException, MalformedURLException, NullPointerException, URISyntaxException {
        ScanCancelRequest scanCancelRequest = new ScanCancelRequest(ncServerURL, ncApiToken, scanTaskId);

        logInfo("Requesting scan cancel...", listener);
        ClassicHttpResponse scanCancelRequestResponse = scanCancelRequest.scanCancelRequest();
        logInfo("Response scan cancel status code: "
                + scanCancelRequestResponse.getCode(), listener);

        ScanCancelRequestResult scanCancelRequestResult = new ScanCancelRequestResult(scanCancelRequestResponse);

        if (scanCancelRequestResult.isError()) {
            logInfo("Scan cancel error", listener);
            logError(scanCancelRequestResult.getErrorMessage(), listener);
            throw new hudson.AbortException("Error when scan cancel!");
        } else {
            logInfo("Scan canceled Id:" + scanTaskId, listener);
        }
    }

    private void ScanRequestFailureHandler(
        ScanRequestResult scanRequestResult, TaskListener listener) throws Exception {
        logError("Scan request failed. Error Message: " + scanRequestResult.getErrorMessage(), listener);

        throw new Exception("Netsparker Enterprise Plugin: Failed to start the scan. Response status code: "
                        + scanRequestResult.getHttpStatusCode());
    }

    private void logInfo(String message, TaskListener listener) {
        listener.getLogger().println("> Netsparker Enterprise Plugin: " + message);
    }

    private void logError(String message, TaskListener listener) {
        listener.error("> Netsparker Enterprise Plugin: " + message);
    }

    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl) super.getDescriptor();
    }

    @Symbol("NCScanBuilder")
    @Extension
    public static final class DescriptorImpl extends BuildStepDescriptor<Builder> {
        private long lastEditorId = 0;
        private ArrayList<WebsiteModel> websiteModels = new ArrayList<>();

        private String ncServerURL;
        private Secret ncApiToken;
        private String rootURL;

        public DescriptorImpl() {
            super(NCScanBuilder.class);
            load();
        }

        public String getNcServerURL() {
            return ncServerURL;
        }

        public void setNcServerURL(String ncServerURL) {
            this.ncServerURL = ncServerURL;
        }

        public Secret getNcApiToken() {
            return ncApiToken;
        }

        public void setNcApiToken(String ncApiToken) {
            this.ncApiToken = Secret.fromString(ncApiToken);
        }

        public String getRootURL() {
            return rootURL;
        }

        @Override
        public boolean isApplicable(Class<? extends AbstractProject> aClass) {
            // Indicates that this builder can be used with all kinds of project types
            return true;
        }

        @Override
        public String getDisplayName() {
            return Messages.NCScanBuilder_DescriptorImpl_DisplayName();
        }

        @Override
        // Invoked when the global configuration page is submitted.
        public boolean configure(StaplerRequest req, JSONObject formData) throws FormException {
            req.bindParameters(this);
            this.ncServerURL = formData.getString("ncServerURL");
            this.ncApiToken = Secret.fromString(formData.getString("ncApiToken"));
            this.rootURL = Jenkins.get().getRootUrl();

            // To persist global configuration information, set properties and call save().
            save();
            return super.configure(req, formData);
        }

        @Override
        public String getConfigPage() {
            try {
                updateWebsiteModels(ncServerURL, ncApiToken);
            } catch (Exception e) {
            }

            return super.getConfigPage();
        }

        @SuppressWarnings("unused")
        public ListBoxModel doFillCredentialsIdItems(@AncestorInPath Item owner,
                @QueryParameter String credentialsId) {
            StandardListBoxModel listBoxModel = new StandardListBoxModel();
            listBoxModel.includeEmptyValue();

            if (owner == null) {
                if (!Jenkins.get().hasPermission(Jenkins.ADMINISTER)) {
                    return listBoxModel.includeCurrentValue(credentialsId);
                }
            } else {
                if (!owner.hasPermission(Item.EXTENDED_READ)
                        && !owner.hasPermission(CredentialsProvider.USE_ITEM)) {
                    return listBoxModel.includeCurrentValue(credentialsId);
                }
            }

            for (StandardUsernamePasswordCredentials credentialToAdd : AppCommon
                    .findCredentials(owner)) {
                listBoxModel.with(credentialToAdd);
            }

            listBoxModel.includeCurrentValue(credentialsId);

            return listBoxModel;
        }

        @JavaScriptMethod
        @SuppressWarnings("unused")
        public synchronized String createEditorId() {
            return String.valueOf(lastEditorId++);
        }

        /*
         * methods named "doFill{fieldname}Items" need to be implemented for to fill select boxes in
         * UI
         */
        @SuppressWarnings("unused")
        public ListBoxModel doFillNcScanTypeItems() {
            ListBoxModel model = new ListBoxModel();
            model.add("Please Select Scan Type", "");
            model.add("Incremental", "Incremental");
            model.add("Full (With primary profile)", "FullWithPrimaryProfile");
            model.add("Full (With selected profile)", "FullWithSelectedProfile");
            
            return model;
        }

        @SuppressWarnings("unused")
        public ListBoxModel doFillNcWebsiteIdItems(@QueryParameter String credentialsId) {

            if (!StringUtils.isEmpty(credentialsId)) {
                doTestConnection(credentialsId);
            }

            ListBoxModel model = new ListBoxModel();
            model.add("Please select website","");
            for (WebsiteModel websiteModel : websiteModels) {
                model.add(websiteModel.getDisplayName(), websiteModel.getId());
            }
                
            return model;
        }

        @SuppressWarnings("unused")
        public ListBoxModel doFillNcProfileIdItems(@QueryParameter String ncWebsiteId) {
            WebsiteModel websiteModel = new WebsiteModel();
            
            for (WebsiteModel wm : websiteModels) {
                if (ncWebsiteId != null && wm.getId().equals(ncWebsiteId)) {
                    websiteModel = wm;
                    break;
                }
            }

            String placeholderText;
            final ArrayList<WebsiteProfileModel> websiteProfileModels = websiteModel.getProfiles();
            ListBoxModel model = new ListBoxModel();
            
            if (websiteProfileModels.isEmpty()) {
                placeholderText = "-- No profile found --";
                model.add(placeholderText, "");
            } else {
                model.add("Please Select Scan Profile","");
                for (WebsiteProfileModel websiteProfileModel : websiteProfileModels) {
                    boolean isSelected =  websiteProfileModels.size() == 1 ? true : false;
                    model.add(new Option(websiteProfileModel.getName(), websiteProfileModel.getId(),isSelected));
                }
            }

            return model;
        }

        @SuppressWarnings("unused")
        public ListBoxModel doFillNcReportTypeItems() {
            ListBoxModel model = new ListBoxModel();
            model.add("Please Select Report Type","");   
            model.add("Detailed Scan Report","ScanDetail");       
            model.add("Executive Summary","ExecutiveSummary"); 
            model.add("Full Scan Detail","FullScanDetail");
            model.add("HIPAA Compliance","HIPAACompliance"); 
            model.add("ISO 27001 Compliance","Iso27001Compliance");
            model.add("Knowledge Base", "KnowledgeBase");     
            model.add("OWASP Top Ten 2013","OwaspTopTen2013");   
            model.add("OWASP Top Ten 2017","OwaspTopTen2017"); 
            model.add("PCI DSS Compliance","PCICompliance");  
            model.add("SANS Top 25", "SansTop25");     
            model.add("WASC Threat Classification","WASC");
            
            return model;
        }

        private int updateWebsiteModels(final String ncServerURL, final Secret ncApiToken)
                throws IOException, URISyntaxException, ParseException {
            WebsiteModelRequest websiteModelRequest =
                    new WebsiteModelRequest(ncServerURL, ncApiToken);
            final ClassicHttpResponse response = websiteModelRequest.getPluginWebSiteModels();
            int statusCode = response.getCode();

            if (statusCode == 200) {
                websiteModels = new ArrayList<>();
                websiteModels.addAll(websiteModelRequest.getWebsiteModels());
            }else if(statusCode == 401){
                websiteModels.clear();
            }

            return statusCode;
        }

        private FormValidation validateConnection(final String ncServerURL,
                final Secret ncApiToken) {
            try {
                int statusCode = updateWebsiteModels(ncServerURL, ncApiToken);
                if (statusCode == 200) {
                    return FormValidation
                            .ok("Successfully connected to the Netsparker Enterprise.");
                } else {
                    return FormValidation
                            .error("Netsparker Enterprise rejected the request. HTTP status code: "
                                    + statusCode);
                }
            } catch (Exception e) {
                return FormValidation
                        .error("Failed to connect to the Netsparker Enterprise. : " + e.toString());
            }
        }

        @POST
        @SuppressWarnings("unused")
        public FormValidation doValidateAPI(@QueryParameter final String ncServerURL,
                @QueryParameter final Secret ncApiToken) {

            Jenkins.get().checkPermission(Jenkins.ADMINISTER);

            return validateConnection(ncServerURL, ncApiToken);
        }

        @SuppressWarnings("unused")
        public FormValidation doTestConnection(@QueryParameter final String credentialsId) {

            final String errorTemplate = "Error: %s";
            try {

                String descriptorUrl = getCurrentDescriptorByNameUrl();

                final StandardUsernamePasswordCredentials credential =
                        AppCommon.findCredentialsById(credentialsId, descriptorUrl);

                if (credential == null) {
                    return FormValidation.error(errorTemplate, "Credentials not found.");
                }

                String serverURL = credential.getUsername();
                Secret apiToken = credential.getPassword();

                return validateConnection(serverURL, apiToken);

            } catch (Exception e) {
                return FormValidation.error(e, errorTemplate, e.getMessage());
            }
        }

        @SuppressWarnings("unused")
        public FormValidation doCheckNcServerURL(@QueryParameter String value) {
            if (value.length() == 0) {
                return FormValidation
                        .error(Messages.NCScanBuilder_DescriptorImpl_errors_missingApiURL());
            } else if (!AppCommon.isUrlValid(value)) {
                return FormValidation
                        .error(Messages.NCScanBuilder_DescriptorImpl_errors_invalidApiURL());
            }

            return FormValidation.ok();
        }

        @POST
        @SuppressWarnings("unused")
        public FormValidation doCheckNcApiToken(@QueryParameter String value) {
            if (value.length() == 0) {
                return FormValidation
                        .error(Messages.NCScanBuilder_DescriptorImpl_errors_missingApiToken());
            }

            return FormValidation.ok();
        }

        @SuppressWarnings("unused")
        public FormValidation doCheckNcScanType(@QueryParameter String value) {
            try {
                ScanType.valueOf(value);
            } catch (Exception ex) {
                return FormValidation
                        .error(Messages.NCScanBuilder_DescriptorImpl_errors_invalidScanType());
            }

            return FormValidation.ok();
        }

        @SuppressWarnings("unused")
        public FormValidation doCheckNcWebsiteId(@QueryParameter String value) {

            if (!AppCommon.isGUIDValid(value)) {
                return FormValidation
                        .error(Messages.NCScanBuilder_DescriptorImpl_errors_invalidWebsiteId());
            }

            return FormValidation.ok();
        }

        @SuppressWarnings("unused")
        public FormValidation doCheckNcReportType(@QueryParameter String value) {

            try {
                ReportType.valueOf(value);
            } catch (Exception ex) {
                return FormValidation
                        .error(Messages.NCScanBuilder_DescriptorImpl_errors_invalidReportType());
            }

            return FormValidation.ok();
        }

        @SuppressWarnings("unused")
        public FormValidation doCheckNcProfileId(@QueryParameter String value, @QueryParameter String ncScanType) {

            boolean isRequired;

            try {
                ScanType type = ScanType.valueOf(ncScanType);
                isRequired = type != ScanType.FullWithPrimaryProfile;
            } catch (Exception ex) {
                return FormValidation
                        .error(Messages.NCScanBuilder_DescriptorImpl_errors_invalidProfileId());
            }

            if (isRequired && !AppCommon.isGUIDValid(value)) {
                return FormValidation
                        .error(Messages.NCScanBuilder_DescriptorImpl_errors_invalidProfileId());
            }

            return FormValidation.ok();
        }

        @SuppressWarnings("unused")
        public ListBoxModel doFillNcSeverityItems() throws IOException {
            ListBoxModel items = new ListBoxModel();
            items.add("Do not fail the build", "DoNotFail");
            items.add("Critical", "Critical");
            items.add("High or above", "Critical,High");
            items.add("Medium or above", "Critical,High,Medium");
            items.add("Low or above", "Critical,High,Medium,Low");
            items.add("Best Practices or above", "Critical,High,Medium,Low,Best Practice");
            return items;
        }
    }
}
