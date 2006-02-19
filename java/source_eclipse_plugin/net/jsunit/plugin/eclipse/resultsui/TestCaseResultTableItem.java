package net.jsunit.plugin.eclipse.resultsui;

import net.jsunit.plugin.eclipse.JsUnitPlugin;
import net.jsunit.plugin.eclipse.resultsui.node.TestCaseResultNode;

import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableItem;

public class TestCaseResultTableItem extends TableItem {

	public TestCaseResultTableItem(Table table, TestCaseResultNode node, NodeLabelProvider labelProvider) {
		super(table, SWT.NONE);
		setText(labelProvider.getText(node));
		setImage(JsUnitPlugin.soleInstance().createImage(node.getImageName()));
		setData(node.getResult());
	}
	
	protected void checkSubclass () {
	}


}