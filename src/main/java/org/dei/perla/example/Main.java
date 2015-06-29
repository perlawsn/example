package org.dei.perla.example;

import org.dei.perla.core.PerLaSystem;
import org.dei.perla.core.Plugin;
import org.dei.perla.core.channel.http.HttpChannelPlugin;
import org.dei.perla.core.descriptor.DataType;
import org.dei.perla.core.engine.Executor;
import org.dei.perla.core.fpc.Fpc;
import org.dei.perla.core.fpc.Task;
import org.dei.perla.core.fpc.TaskHandler;
import org.dei.perla.core.message.json.JsonMapperFactory;
import org.dei.perla.core.sample.Attribute;
import org.dei.perla.core.sample.Sample;

import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

public class Main {

	private static final String descFile = "src/main/resources/weather_mi.xml";

	public static void main(String[] args) throws Exception {
        List<Plugin> plugins = new ArrayList<>();
        plugins.add(new JsonMapperFactory());
        plugins.add(new HttpChannelPlugin());
        PerLaSystem sys = new PerLaSystem(plugins);

		System.out.println("Creating FPC from descriptor " + descFile + "...");
        Fpc fpc = sys.injectDescriptor(new FileInputStream(descFile));

		System.out.println("Requesting data...");
		List<Attribute> atts = new ArrayList<>();
		atts.add(Attribute.create("temp_c", DataType.FLOAT));
		PrintHandler ph = new PrintHandler();
		fpc.get(atts, ph); // Single shot
		// fpc.get(atts, 1000, ph); // Periodic

		ph.waitCompletion();
		fpc.stop((f) -> {
			try {
				Executor.shutdown(10);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		});
	}

	private static class PrintHandler implements TaskHandler {

		ReentrantLock l = new ReentrantLock();
		Condition c = l.newCondition();

		@Override
		public void complete(Task task) {
			l.lock();
			try {
				System.out.println("Task completed successfully");
				c.signalAll();
			} finally {
				l.unlock();
			}
		}

		@Override
		public void data(Task task, Sample r) {
			System.out.println("New sample received: ");
            List<Attribute> atts = r.fields();
            Object[] fields = r.values();
            for (int i = 0; i < atts.size(); i++) {
                System.out.println(atts.get(i).getId() + ": " + fields[i]);
            }
		}

		@Override
		public void error(Task task, Throwable cause) {
			l.lock();
			try {
				System.out.println("Error: " + cause);
				c.signalAll();
			} finally {
				l.unlock();
			}
		}

		public void waitCompletion() throws InterruptedException {
			l.lock();
			try {
				c.await();
			} finally {
				l.unlock();
			}
		}

	}

}
