package net.jsunit.plugin.eclipse.resultsui;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;

import net.jsunit.RemoteTestRunClient;
import net.jsunit.TestRunListener;
import net.jsunit.model.BrowserResult;
import net.jsunit.plugin.eclipse.JsUnitPlugin;
import net.jsunit.plugin.eclipse.resultsui.action.CollapseAllAction;
import net.jsunit.plugin.eclipse.resultsui.action.ExpandAllAction;
import net.jsunit.plugin.eclipse.resultsui.action.StopAction;
import net.jsunit.plugin.eclipse.resultsui.action.ToggleFullyQualifiedNodeNamesAction;
import net.jsunit.plugin.eclipse.resultsui.tab.AllTestsByBrowserResultsTab;
import net.jsunit.plugin.eclipse.resultsui.tab.FailuresTestResultsTab;

import org.eclipse.jface.action.IToolBarManager;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.CLabel;
import org.eclipse.swt.custom.CTabFolder;
import org.eclipse.swt.custom.SashForm;
import org.eclipse.swt.custom.ViewForm;
import org.eclipse.swt.dnd.Clipboard;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.ToolBar;
import org.eclipse.ui.IActionBars;
import org.eclipse.ui.part.ViewPart;

public class JsUnitTestResultsViewPart extends ViewPart implements TestRunListener, ActiveTabSource, TestRunStoppable {
	
	static public final String ID = "net.jsunit.plugin.eclipse.resultsui.JsUnitTestResultsViewPart";

	private JsUnitProgressBar progressBar;
	private ContentProvider contentProvider;
	private Composite counterComposite;
	private CounterPanel counterPanel;
	private SashForm sashForm;
	private CTabFolder tabFolder;
	private FailureTrace failureTrace;
	private Clipboard clipboard;
	private List<TestResultsTab> testResultsTabs;
	private TestResultsTab activeTab;
	private StopAction stopAction;
	private CollapseAllAction collapseAllAction;
	private ExpandAllAction expandAllAction;
	private ToggleFullyQualifiedNodeNamesAction toggleFullyQualifiedNodeNamesAction;
	private RemoteTestRunClient client;
	private long startTime;
	private boolean stopped;
	private NodeLabelProvider labelProvider;

	public void createPartControl(Composite parent) {
		contentProvider = new ContentProvider(getViewSite());
		labelProvider = new NodeLabelProvider();
		GridLayout gridLayout = new GridLayout(); 
		gridLayout.marginWidth = 0;
		gridLayout.marginHeight = 0;
		parent.setLayout(gridLayout);

		counterComposite = createProgressCountPanel(parent);
		counterComposite.setLayoutData(new GridData(GridData.GRAB_HORIZONTAL | GridData.HORIZONTAL_ALIGN_FILL));

		SashForm sashForm = createSashForm(parent);
		sashForm.setOrientation(SWT.HORIZONTAL);
		sashForm.setLayoutData(new GridData(GridData.FILL_BOTH));
		
		stopAction = new StopAction(this, JsUnitPlugin.soleInstance());
		expandAllAction = new ExpandAllAction(this, JsUnitPlugin.soleInstance());
		collapseAllAction = new CollapseAllAction(this, JsUnitPlugin.soleInstance());
		toggleFullyQualifiedNodeNamesAction = new ToggleFullyQualifiedNodeNamesAction(this, JsUnitPlugin.soleInstance(), labelProvider);
		configureToolBar();
	}
	
	private SashForm createSashForm(Composite parent) {
		clipboard = new Clipboard(parent.getDisplay());

		sashForm = new SashForm(parent, SWT.VERTICAL);
		
		ViewForm left = new ViewForm(sashForm, SWT.NONE);

		ViewForm right = new ViewForm(sashForm, SWT.NONE);
		CLabel label= new CLabel(right, SWT.NONE);
		label.setText("Failures");
		label.setImage(JsUnitPlugin.soleInstance().createImage("stkfrm_obj.gif"));
		right.setTopLeft(label);

		ToolBar failureToolBar = new ToolBar(right, SWT.FLAT | SWT.WRAP);
		right.setTopCenter(failureToolBar);
		failureTrace= new FailureTrace(right, clipboard, this, failureToolBar);
		right.setContent(failureTrace.getComposite()); 
		
		tabFolder = createTestRunTabs(left);
		tabFolder.setLayoutData(new TabFolderLayout());
		left.setContent(tabFolder);

		sashForm.setWeights(new int[]{50, 50});
		return sashForm;
	}
	
	protected CTabFolder createTestRunTabs(Composite parent) {
		CTabFolder tabFolder = new CTabFolder(parent, SWT.TOP);
		tabFolder.setLayoutData(new GridData(GridData.FILL_BOTH | GridData.GRAB_VERTICAL));
		testResultsTabs = new ArrayList<TestResultsTab>();

		testResultsTabs.add(new AllTestsByBrowserResultsTab(tabFolder, getViewSite(), contentProvider, failureTrace, labelProvider));
		//TODO: implement AllTestsByTestResultsTab
//		testResultsTabs.add(new AllTestsByTestResultsTab(tabFolder, getViewSite(), contentProvider, failureTrace, labelProvider));
		testResultsTabs.add(new FailuresTestResultsTab(tabFolder, getViewSite(), contentProvider, failureTrace, labelProvider));
		
		tabFolder.setSelection(0);				
		activeTab = (TestResultsTab) testResultsTabs.get(0);		
				
		tabFolder.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent event) {
				testResultsTabChanged(event);
			}
		});
		return tabFolder;
	}
	
	private void testResultsTabChanged(SelectionEvent event) {
		for (TestResultsTab tab : testResultsTabs) {
			if (((CTabFolder) event.widget).getSelection().getText().equals(tab.getName()))
				activeTab= tab;
				activeTab.refresh();
				collapseAllAction.setEnabled(activeTab.isHierarchical());
				expandAllAction.setEnabled(activeTab.isHierarchical());
		}
	}
	
	protected Composite createProgressCountPanel(Composite parent) {
		Composite composite= new Composite(parent, SWT.NONE);
		GridLayout layout= new GridLayout();
		composite.setLayout(layout);
		layout.numColumns = 2;
		counterPanel = new CounterPanel(composite);
		counterPanel.setLayoutData(new GridData(GridData.GRAB_HORIZONTAL | GridData.HORIZONTAL_ALIGN_FILL));
		progressBar = new JsUnitProgressBar(composite);
		progressBar.setLayoutData(new GridData(GridData.GRAB_HORIZONTAL | GridData.HORIZONTAL_ALIGN_FILL));
		return composite;
	}
	
	public void setFocus() {
		activeTab.setFocus();
	}

	private void refreshActiveTab() {
		activeTab.refresh();
	}

	public void reset() {
		contentProvider.reset();
		resetBrowserCountAndProgressBar();
		refreshActiveTab();
	}

	private void resetBrowserCountAndProgressBar() {
		final int browserCount = contentProvider.getBrowserResultNodes().size();
		JsUnitPlugin.getDisplay().asyncExec(new Runnable() {
			public void run() {
				progressBar.reset();
				counterPanel.reset();
				progressBar.setMaximum(browserCount);
				counterPanel.setTotalBrowserCount(browserCount);
				failureTrace.clear();
			}
		});
	}
	
	private void configureToolBar() {
		IActionBars actionBars= getViewSite().getActionBars();
		IToolBarManager toolBar= actionBars.getToolBarManager();
		stopAction.setEnabled(false);
		toolBar.add(stopAction);
		expandAllAction.setEnabled(true);
		toolBar.add(expandAllAction);
		collapseAllAction.setEnabled(true);
		toolBar.add(collapseAllAction);
		toggleFullyQualifiedNodeNamesAction.setEnabled(true);
		toolBar.add(toggleFullyQualifiedNodeNamesAction);
		actionBars.updateActionBars();
	}
	
	public boolean isReady() {
		return true;
	}

	public void testRunStarted() {
		stopped = false;
		stopAction.setEnabled(true);
		contentProvider.testRunStarted();
		resetBrowserCountAndProgressBar();
		refreshActiveTab();
		startTime = System.currentTimeMillis();
		setContentDescriptionMessage("Starting Test Run");
	}

	public void testRunFinished() {
		stopAction.setEnabled(false);
		contentProvider.testRunFinished();
		if (activeTab.isHierarchical())
			refreshActiveTab();
		long millisTaken = System.currentTimeMillis() - startTime;
		String message = stopped ? "Test Run stopped after " : "Test Run took ";
		message += (NumberFormat.getInstance().format(millisTaken / 1000d) + " seconds");
		setContentDescriptionMessage(message);
	}

	private void setContentDescriptionMessage(final String message) {
		JsUnitPlugin.getDisplay().asyncExec(new Runnable() {
			public void run() {
				setContentDescription(message);
			}
		});
	}
	

	public void browserTestRunFinished(String browserFileName, final BrowserResult result) {
		contentProvider.browserTestRunFinished(browserFileName, result);
		JsUnitPlugin.getDisplay().asyncExec(new Runnable() {
			public void run() {
				if (!result.completedTestRun())
					progressBar.setError();
				progressBar.step(contentProvider.getTestFailureCount() + contentProvider.getTestErrorCount());
				counterPanel.setBrowserRunCount(contentProvider.getCompletedBrowserTestRunCount());
				counterPanel.setTestRunCount(contentProvider.getCompletedTestCount());
				counterPanel.setTestErrorCount(contentProvider.getTestErrorCount());
				counterPanel.setTestFailureCount(contentProvider.getTestFailureCount());
			}
		});
		refreshActiveTab();
		setContentDescriptionMessage("Done running tests in browser " + browserFileName);
	}

	public void browserTestRunStarted(String browserFileName) {
		contentProvider.browserTestRunStarted(browserFileName);
		refreshActiveTab();
		setContentDescriptionMessage("Running tests in browser "+browserFileName);
	}

	public void connectToRemoteRunner(int serverPort) {
		client = new RemoteTestRunClient(this, serverPort);
		client.startListening();
	}

	public TestResultsTab getActiveTab() {
		return activeTab;
	}

	public void stopTestRun() {
		stopped = true;
		client.sendStopServer();
		client.stopListening();
		progressBar.stopped();
		testRunFinished();		
	}
	
	

}