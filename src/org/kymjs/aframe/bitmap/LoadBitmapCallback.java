/*
 * Copyright (c) 2014, KJFrameForAndroid 张涛 (kymjs123@gmail.com).
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.kymjs.aframe.bitmap;

import android.graphics.Bitmap;

/**
 * 用作KJBitmap.loadBmp参数，从网络获取一个Bitmap的回调方法<br>
 * <b>创建时间：</b> 2014-9-19<br>
 * 
 * @author kymjs(kymjs123@gmail.com)
 * @version 1.0
 */
public interface LoadBitmapCallback {
    /**
     * 对Bitmap做某事
     * 
     * @param bitmap
     */
    void doSomething(Bitmap bitmap);
}
