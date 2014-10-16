/*
 * Copyright (c) 2014, kymjs 张涛 (kymjs123@gmail.com).
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
package org.kymjs.aframe.utils;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.channels.FileChannel;

import org.kymjs.aframe.KJLoger;
import org.kymjs.aframe.bitmap.utils.BitmapCreate;
import org.kymjs.aframe.bitmap.utils.BitmapHelper;
import org.kymjs.aframe.bitmap.utils.BitmapOperateUtil;
import org.kymjs.aframe.core.KJException;

import android.app.Activity;
import android.content.Context;
import android.content.CursorLoader;
import android.content.res.Resources.Theme;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Bitmap.CompressFormat;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;

/**
 * 文件与流处理工具类<br>
 * 
 * <b>创建时间</b> 2014-8-14
 * 
 * @author kymjs(kymjs123@gmail.com)
 * @version 1.1
 */
public final class FileUtils {
    private static final String TAG = "FileUtils";

	/**
     * 检测SD卡是否存在
     */
    public static boolean checkSDcard() {
        return Environment.MEDIA_MOUNTED.equals(Environment
                .getExternalStorageState());
    }

    /**
     * 将文件保存到本地
     */
    public static void saveFileCache(byte[] fileData,
            String folderPath, String fileName) {
        File folder = new File(folderPath);
        folder.mkdirs();
        File file = new File(folderPath, fileName);
        ByteArrayInputStream is = new ByteArrayInputStream(fileData);
        OutputStream os = null;
        if (!file.exists()) {
            try {
                file.createNewFile();
                os = new FileOutputStream(file);
                byte[] buffer = new byte[1024];
                int len = 0;
                while (-1 != (len = is.read(buffer))) {
                    os.write(buffer, 0, len);
                }
                os.flush();
            } catch (Exception e) {
                throw new KJException(FileUtils.class.getClass()
                        .getName(), e);
            } finally {
                closeIO(is, os);
            }
        }
    }

    /**
     * 从指定文件夹获取文件
     * 
     * @return 如果文件不存在则创建,如果如果无法创建文件或文件名为空则返回null
     */
    public static File getSaveFile(String folderPath, String fileNmae) {
        File file = new File(getSavePath(folderPath) + File.separator
                + fileNmae);
        try {
            file.createNewFile();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return file;
    }

    /**
     * 获取SD卡下指定文件夹的绝对路径
     * 
     * @return 返回SD卡下的指定文件夹的绝对路径
     */
    public static String getSavePath(String folderName) {
        return getSaveFolder(folderName).getAbsolutePath();
    }

    /**
     * 获取文件夹对象
     * 
     * @return 返回SD卡下的指定文件夹对象，若文件夹不存在则创建
     */
    public static File getSaveFolder(String folderName) {
        File file = new File(Environment
                .getExternalStorageDirectory().getAbsoluteFile()
                + File.separator + folderName + File.separator);
        file.mkdirs();
        return file;
    }

    /**
     * 输入流转byte[]<br>
     * 
     * <b>注意</b> 你必须手动关闭参数inStream
     */
    public static final byte[] input2byte(InputStream inStream) {
        if (inStream == null) {
            return null;
        }
        byte[] in2b = null;
        ByteArrayOutputStream swapStream = new ByteArrayOutputStream();
        byte[] buff = new byte[100];
        int rc = 0;
        try {
            while ((rc = inStream.read(buff, 0, 100)) > 0) {
                swapStream.write(buff, 0, rc);
            }
            in2b = swapStream.toByteArray();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            closeIO(swapStream);
        }
        return in2b;
    }

    /**
     * 把uri转为File对象
     */
    public static File uri2File(Activity aty, Uri uri) {
        if (SystemTool.getSDKVersion() < 11) {
            // 在API11以下可以使用：managedQuery
            String[] proj = { MediaStore.Images.Media.DATA };
            @SuppressWarnings("deprecation")
            Cursor actualimagecursor = aty.managedQuery(uri, proj,
                    null, null, null);
            int actual_image_column_index = actualimagecursor
                    .getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
            actualimagecursor.moveToFirst();
            String img_path = actualimagecursor
                    .getString(actual_image_column_index);
            return new File(img_path);
        } else {
            // 在API11以上：要转为使用CursorLoader,并使用loadInBackground来返回
            String[] projection = { MediaStore.Images.Media.DATA };
            CursorLoader loader = new CursorLoader(aty, uri,
                    projection, null, null, null);
            Cursor cursor = loader.loadInBackground();
            int column_index = cursor
                    .getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
            cursor.moveToFirst();
            return new File(cursor.getString(column_index));
        }
    }
    /**
     * 拷贝目录及目录下的所有文件(注意:如果是安卓程序,请放在子线程中进行)
     * @param fromDir 源目录
     * @param toDir 目标目录(如果不存在,则创建)
     * @return 复制目录成功或失败(失败情况很多种:传入参数不正确,部分或所有文件未拷贝完成)
     */
    public static boolean copyDir2Dir(File fromDir, File toDir){
    	boolean isSuccess = false;
    	if (null == fromDir || !fromDir.exists() || !fromDir.isDirectory() || null == toDir) {
			return isSuccess;
		}
    	if (!toDir.exists()) {
			toDir.mkdirs();
		}
    	if (!toDir.isDirectory()) {
			return isSuccess;
		}
    	File[] files = fromDir.listFiles();
    	if (files != null && files.length > 0) {
			for(File file : files){
				String oldFileName = file.getName();
				String newFileName = toDir.getAbsolutePath() + File.separator + oldFileName;
				File newFile = new File(newFileName);
				if (file.isFile()) {
					if (copyFile(file, newFile) > 0) {
						isSuccess = true;
					}else {
						isSuccess = false;
					};
				}else {
					newFile.mkdir();
					copyDir2Dir(file, newFile);
					isSuccess = true;
				}
			}
		}
    	return isSuccess;
    }

    /**
     * 复制文件
     * 
     * @param from 源文件
     * @param to 目标文件
     * @return 复制的字节数,复制失败返回-1
     */
    public static long copyFile(File from, File to) {
    	long copyedBytes = -1;
        if (null == from || !from.exists() || null == to) {
            return copyedBytes;
        }
        FileInputStream is = null;
        FileOutputStream os = null;
        try {
            is = new FileInputStream(from);
            if (!to.exists()) {
				to.createNewFile();
			}
            os = new FileOutputStream(to);
            copyedBytes = copyFileFast(is, os);
        } catch (Exception e) {
            throw new KJException(FileUtils.class.getClass().getName(), e);
        } finally {
            closeIO(is, os);
        }
        return copyedBytes;
    }

    /**
     * 快速复制文件（采用nio操作）
     * 
     * @param is
     *            数据来源
     * @param os
     *            数据目标
     * @throws IOException
     */
    public static long copyFileFast(FileInputStream is,
            FileOutputStream os) throws IOException {
        FileChannel in = is.getChannel();
        FileChannel out = os.getChannel();
        return in.transferTo(0, in.size(), out);
    }

    /**
     * 关闭流
     * 
     * @param closeables
     */
    public static void closeIO(Closeable... closeables) {
        if (null == closeables || closeables.length <= 0) {
            return;
        }
        for (Closeable cb : closeables) {
            try {
                if (null == cb) {
                    continue;
                }
                cb.close();
            } catch (IOException e) {
                throw new KJException(FileUtils.class.getClass()
                        .getName(), e);
            }
        }
    }

    /**
     * 图片写入文件
     * 
     * @param bitmap
     *            图片
     * @param filePath
     *            文件路径
     * @return 是否写入成功
     */
    public static boolean bitmapToFile(Bitmap bitmap, String filePath) {
        boolean isSuccess = false;
        if (bitmap == null) {
            return isSuccess;
        }
        OutputStream out = null;
        try {
            out = new BufferedOutputStream(new FileOutputStream(
                    filePath), 8 * 1024);
            isSuccess = bitmap.compress(CompressFormat.PNG, 70, out);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } finally {
            closeIO(out);
        }
        return isSuccess;
    }

    /**
     * 从文件中读取文本
     * 
     * @param filePath
     * @return
     */
    public static String readFile(String filePath) {
        InputStream is = null;
        try {
            is = new FileInputStream(filePath);
        } catch (Exception e) {
            throw new KJException(FileUtils.class.getName()
                    + "readFile---->" + filePath + " not found");
        }
        return inputStream2String(is);
    }

    /**
     * 从assets中读取文本
     * 
     * @param name
     * @return
     */
    public static String readFileFromAssets(Context context,
            String name) {
        InputStream is = null;
        try {
            is = context.getResources().getAssets().open(name);
        } catch (Exception e) {
            throw new KJException(FileUtils.class.getName()
                    + ".readFileFromAssets---->" + name
                    + " not found");
        }
        return inputStream2String(is);
    }

    /**
     * 输入流转字符串
     * 
     * @param is
     * @return 一个流中的字符串
     */
    public static String inputStream2String(InputStream is) {
        if (null == is) {
            return null;
        }
        StringBuilder resultSb = null;
        try {
            BufferedReader br = new BufferedReader(
                    new InputStreamReader(is));
            resultSb = new StringBuilder();
            String len;
            while (null != (len = br.readLine())) {
                resultSb.append(len);
            }
        } catch (Exception ex) {
        } finally {
            closeIO(is);
        }
        return null == resultSb ? null : resultSb.toString();
    }

    /**
     * 纠正图片角度（有些相机拍照后相片会被系统旋转）
     * 
     * @param path
     *            图片路径
     */
    public static void correctPictureAngle(String path) {
        int angle = BitmapOperateUtil.readPictureDegree(path);
        if (angle != 0) {
            Bitmap image = BitmapHelper.rotate(angle,
                    BitmapCreate.bitmapFromFile(path, 1000, 1000));
            bitmapToFile(image, path);
        }
    }
}