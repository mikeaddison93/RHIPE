/**
 * Copyright 2009 Saptarshi Guha
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.godhuli.rhipe;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.io.WritableComparable;
import org.apache.hadoop.mapreduce.Mapper;
import org.apache.hadoop.mapreduce.lib.input.FileSplit;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Arrays;

public class RHMRMapper extends Mapper<WritableComparable, RHBytesWritable, WritableComparable, RHBytesWritable> {
    protected static final Log LOG = LogFactory.getLog(RHMRMapper.class.getName());
    int whichMapper = 1; // 1 is usekeys and values, 0 is just values
    boolean copyFile = false;

    String getPipeCommand(final Configuration cfg) {
        String str = System.getenv("RHIPECOMMAND");
        if (str == null) {
            str = cfg.get("rhipe_command");
            if (str == null) {
               LOG.warn("No rhipe_command");
            }
        }
        return (str);
    }

    boolean getDoPipe() {
        return true;
    }

    public void run(final Context context) throws IOException, InterruptedException {
        dumpClasspath(this.getClass().getClassLoader());
        final long t1 = System.currentTimeMillis();
        helper = new RHMRHelper("Mapper", context.getJobID().toString(),context.getTaskAttemptID().getTaskID().toString());
        setup(context);
        if (whichMapper == 1) {
            while (context.nextKeyValue()) {
                // System.err.println(context.getCurrentKey());
                // System.err.println(context.getCurrentValue());
                map(context.getCurrentKey(), context.getCurrentValue(), context);
            }
        }
        else if (whichMapper == 0) {
            while (context.nextKeyValue()) {
                // System.err.println(context.getCurrentKey());
                // System.err.println(context.getCurrentValue());
                map_no_keys(context.getCurrentValue(), context);
            }
        }
        cleanup(context);
        helper.checkOuterrThreadsThrowable();
        context.getCounter("rhipe_timing", "overall_mapper_ms").increment(System.currentTimeMillis() - t1);
    }

    public void dumpClasspath(ClassLoader loader) {
        System.out.println("Classloader " + loader + ":");

        if (loader instanceof URLClassLoader) {
            URLClassLoader ucl = (URLClassLoader) loader;
            URL[] urLs = ucl.getURLs();
            for (URL urL : urLs) {
                System.out.println(urL);
            }
        } else
            System.out.println("\t(cannot display components as not a URLClassLoader)");

        if (loader.getParent() != null)
            dumpClasspath(loader.getParent());
    }

    public void setup(final Context context) {
        final Configuration cfg = context.getConfiguration();

        // Test External Jar File is Present!
        // RHMRHelper.invoke("org.godhuli.rhipe.HBase.TestCase","showMessage",new Class[]{String.class}, new Object[]{new String("Foo")});
        try {
            final String mif = ((FileSplit) context.getInputSplit()).getPath().toString();
            cfg.set("mapred.input.file", mif);
        }
        catch (java.lang.ClassCastException e) {
            //ignore
        }
        cfg.set("RHIPEWHAT", "0");
        LOG.debug("mapred.input.file == " + cfg.get("mapred.input.file"));
        helper.setup(cfg, getPipeCommand(cfg), getDoPipe());
        copyFile = cfg.get("rhipe_copy_file").equals("TRUE");
        whichMapper = cfg.getInt("rhipe_send_keys_to_map", 1);
        helper.startOutputThreads(context);

        try {
            helper.writeCMD(RHTypes.EVAL_SETUP_MAP);
            helper.checkOuterrThreadsThrowable();
        }
        catch (IOException e) {
            e.printStackTrace();
            helper.mapRedFinished(context);
            throw new RuntimeException(e);
        }
    }


    public void map(final WritableComparable key, final RHBytesWritable value, final Context ctx) throws IOException, InterruptedException {
        helper.checkOuterrThreadsThrowable();
        try {
            helper.write(key);
            helper.write(value);
        }
        catch (IOException io) {
            LOG.info("QUIIIITING:" + helper.exitval());
            io.printStackTrace();
            helper.mapRedFinished(ctx);
            throw new IOException(io);
        }
    }

    public void map_no_keys(final RHBytesWritable value, final Context ctx) throws IOException, InterruptedException {
        helper.checkOuterrThreadsThrowable();
        try {
            helper.write(value);
        }
        catch (IOException io) {
            LOG.info("QUIIIITING:" + helper.exitval());
            io.printStackTrace();
            helper.mapRedFinished(ctx);
            throw new IOException(io);
        }
    }

    public void cleanup(final Context ctx) {
        try {
            helper.writeCMD(RHTypes.EVAL_CLEANUP_MAP);
            helper.writeCMD(RHTypes.EVAL_FLUSH);
            helper.checkOuterrThreadsThrowable();
            helper.mapRedFinished(ctx);
            helper.copyFiles(System.getProperty("java.io.tmpdir"));
        }
        catch (IOException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    private RHMRHelper helper;
}

