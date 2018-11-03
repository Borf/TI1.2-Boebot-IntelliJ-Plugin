package com.avans.boebotplugin.services;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.util.xmlb.XmlSerializerUtil;

@State(
		name="BoeBotSettings",
		storages = {
				@Storage("BoeBotSettings.xml")}
)
public class Settings implements PersistentStateComponent<Settings> {
	public String ip = "10.10.10.1";


	public Settings getState() {
		return this;
	}

	public void loadState(Settings state) {
		XmlSerializerUtil.copyBean(state, this);
	}
}