/*
 * Copyright (c) 2014, KJFrameForAndroid 张涛 (kymjs123@gmail.com).
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.kymjs.aframe.ui.fragment;

import org.kymjs.aframe.utils.LogUtils;

import android.os.Bundle;

/**
 * Application's base Fragment,you should inherit it for your Fragment<br>
 * 
 * <b>创建时间</b> 2014-5-28
 * 
 * @author kymjs(kymjs123@gmail.com)
 * @version 1.0
 */
public abstract class BaseFragment extends KJFrameFragment {

    /***************************************************************************
     * 
     * print Fragment callback methods
     * 
     ***************************************************************************/
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LogUtils.d("---------onCreateView ");
    }

    @Override
    public void onResume() {
        LogUtils.d("---------onResume ");
        super.onResume();
    }

    @Override
    public void onPause() {
        LogUtils.d("---------onPause ");
        super.onPause();
    }

    @Override
    public void onStop() {
        LogUtils.d("---------onStop ");
        super.onStop();
    }

    @Override
    public void onDestroyView() {
        LogUtils.d("---------onDestroy ");
        super.onDestroyView();
    }
}
