package com.piusvelte.wapdroid;

interface IWapdroidUI {
	void locationChanged(String mCID, String mLAC, String mMNC, String mMCC);
	void signalChanged(String mRSSI);}