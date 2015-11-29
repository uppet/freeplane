package org.freeplane.plugin.script;

import static org.freeplane.plugin.script.ScriptingMenuUtils.noScriptsAvailableMessage;
import static org.freeplane.plugin.script.ScriptingMenuUtils.scriptNameToMenuItemTitle;

import java.awt.event.ActionEvent;
import java.util.HashSet;
import java.util.Map;

import javax.swing.Action;

import org.freeplane.core.ui.AFreeplaneAction;
import org.freeplane.core.ui.menubuilders.generic.BuildPhaseListener;
import org.freeplane.core.ui.menubuilders.generic.Entry;
import org.freeplane.core.ui.menubuilders.generic.EntryAccessor;
import org.freeplane.core.ui.menubuilders.generic.EntryNavigator;
import org.freeplane.core.ui.menubuilders.generic.EntryVisitor;
import org.freeplane.core.ui.menubuilders.generic.PhaseProcessor.Phase;
import org.freeplane.core.util.ActionUtils;
import org.freeplane.core.util.TextUtils;
import org.freeplane.plugin.script.ExecuteScriptAction.ExecutionMode;
import org.freeplane.plugin.script.ScriptingConfiguration.ScriptMetaData;

public class ScriptingMenuEntryVisitor implements EntryVisitor, BuildPhaseListener {
	private ScriptingConfiguration configuration;
	private ExecutionModeSelector modeSelector;
	private final HashSet<String> registeredLocations = new HashSet<String>();
	private EntryNavigator entryNavigator;

	public ScriptingMenuEntryVisitor(ScriptingConfiguration configuration, ExecutionModeSelector modeSelector) {
		this.configuration = configuration;
		this.modeSelector = modeSelector;
	}

	private EntryNavigator initEntryNavigator(Entry scriptingEntry) {
		if (entryNavigator == null) {
			entryNavigator = new EntryNavigator();
			final Entry userScriptsEntry = scriptingEntry.getParent();
			// TODO: read this mapping from config file
			entryNavigator.addAlias("main_menu_scripting" + "/scripts", userScriptsEntry.getPath());
			entryNavigator.addAlias("main_menu_scripting", userScriptsEntry.getParent().getPath());
			entryNavigator.addAlias("/menu_bar/help", "main_menu/help/help_misc");
			entryNavigator.addAlias("/menu_bar", "main_menu");
		}
		return entryNavigator;
    }

	@Override
	public void visit(Entry target) {
		initEntryNavigator(target);
		if (configuration.getMenuTitleToPathMap().isEmpty()) {
			target.addChild(createNoScriptsAvailableAction());
		}
		else {
			// add entry for all scripts but disable scripts that don't support selected exec mode  
			final ExecutionMode executionMode = modeSelector.getExecutionMode();
			for (final Map.Entry<String, String> entry : configuration.getMenuTitleToPathMap().entrySet()) {
				String scriptName = entry.getKey();
				final ScriptMetaData metaData = configuration.getMenuTitleToMetaDataMap().get(scriptName);
				if (!metaData.hasMenuLocation()) {
					final Entry menuEntry = createEntry(scriptName, entry.getValue(), executionMode);
    				// System.out.println("adding " + metaData);
    				target.addChild(menuEntry);
				}
				// else: see buildPhaseFinished
			}
		}
	}

	@Override
	public void buildPhaseFinished(Phase actions, Entry target) {
		if (actions == Phase.ACTIONS) 
			System.out.print("buildPhaseFinished: " + target.getPath());
		if (target.getParent() == null && actions == Phase.ACTIONS) {
			for (final Map.Entry<String, String> entry : configuration.getMenuTitleToPathMap().entrySet()) {
				final ScriptMetaData metaData = configuration.getMenuTitleToMetaDataMap().get(entry.getKey());
				if (metaData.hasMenuLocation()) {
					addEntryForGivenLocation(target.getRoot(), metaData, entry.getValue());
				}
				// else: see visit
			}
		}
	}

	private void addEntryForGivenLocation(Entry rootEntry, final ScriptMetaData metaData, String scriptPath) {
		for (final ExecutionMode executionMode : metaData.getExecutionModes()) {
			final String location = metaData.getMenuLocation(executionMode);
			if (registeredLocations.add(location + "/" + metaData.getScriptName())) {
				Entry parentEntry = findOrCreateEntry(rootEntry, location);
				if (parentEntry == null)
					throw new RuntimeException("internal error: cannot add entry for " + location);
				Entry entry = createEntry(metaData.getScriptName(), scriptPath, executionMode);
				parentEntry.addChild(entry);;
			}
		}
	}

	private Entry findOrCreateEntry(Entry rootEntry, final String path) {
		Entry entry = entryNavigator.findChildByPath(rootEntry, path);
		if (entry == null) {
			Entry parent = findOrCreateEntry(rootEntry, ScriptingMenuUtils.parentLocation(path));
			Entry menuEntry = new Entry();
			menuEntry.setName(lastPathElement(path));
			menuEntry.setAttribute("text", scriptNameToMenuItemTitle(lastPathElement(path)));
			parent.addChild(menuEntry);
			return menuEntry;
		}
		return entry;
	}

    private String lastPathElement(String path) {
    	int indexOfSlash = path.lastIndexOf('/');
    	// even works if not found (-1 + 1 = 0)
	    return path.substring(indexOfSlash + 1);
    }

	private Entry createNoScriptsAvailableAction() {
		final Entry entry = new Entry();
		entry.setName("NoScriptsAvailableAction");
		@SuppressWarnings("serial")
		final AFreeplaneAction noScriptsAvailableAction = new AFreeplaneAction("NoScriptsAvailableAction", noScriptsAvailableMessage(), null) {
			@Override
			public void actionPerformed(ActionEvent e) {
			}
		};
		new  EntryAccessor().setAction(entry, noScriptsAvailableAction);
		return entry;
	}

	private Entry createEntry(final String scriptName, final String scriptPath, ExecutionMode executionMode) {
		final ScriptMetaData metaData = configuration.getMenuTitleToMetaDataMap().get(scriptName);
		final String title = scriptNameToMenuItemTitle(scriptName);
		return createEntry(createAction(scriptName, scriptPath, executionMode, metaData, title));
	}

	private Entry createEntry(AFreeplaneAction action) {
	    final EntryAccessor entryAccessor = new EntryAccessor();
		final Entry scriptEntry = new Entry();
		entryAccessor.addChildAction(scriptEntry, action);
		entryAccessor.setIcon(scriptEntry, ActionUtils.getActionIcon(action));
		return scriptEntry;
    }

	private AFreeplaneAction createAction(final String scriptName, final String scriptPath,
                                          ExecutionMode executionMode, final ScriptMetaData metaData, final String title) {
	    AFreeplaneAction action = new ExecuteScriptAction(scriptName, title, scriptPath, executionMode,
		    metaData.cacheContent(), metaData.getPermissions());
		action.setEnabled(metaData.getExecutionModes().contains(executionMode));
		String tooltip = createTooltip(title, metaData);
		action.putValue(Action.SHORT_DESCRIPTION, tooltip);
		action.putValue(Action.LONG_DESCRIPTION, tooltip);
	    return action;
    }

	private String createTooltip(String title, ScriptMetaData metaData) {
		final StringBuffer tooltip = new StringBuffer("<html>") //
		    .append(TextUtils.format(ScriptingMenuUtils.LABEL_AVAILABLE_MODES_TOOLTIP, title)) //
		    .append("<ul>");
		for (ExecutionMode executionMode : metaData.getExecutionModes()) {
			tooltip.append("<li>");
			tooltip.append(getTitleForExecutionMode(executionMode));
			tooltip.append("</li>");
		}
		tooltip.append("</ul>");
		return tooltip.toString();
	}

    private String getTitleForExecutionMode(ExecutionMode executionMode) {
        final String scriptLabel = TextUtils.getText(ScriptingMenuUtils.LABEL_SCRIPT);
        return TextUtils.format(ScriptingConfiguration.getExecutionModeKey(executionMode), scriptLabel);
    }

	@Override
	public boolean shouldSkipChildren(Entry entry) {
		return true;
	}
}
