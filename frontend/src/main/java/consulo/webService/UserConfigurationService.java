package consulo.webService;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Properties;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicLong;

import javax.annotation.PostConstruct;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.FileSystemUtils;
import com.google.common.annotations.VisibleForTesting;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.SystemProperties;
import consulo.webService.plugins.PluginAnalyzerService;
import consulo.webService.plugins.PluginChannel;
import consulo.webService.plugins.PluginChannelService;
import consulo.webService.util.ConsuloHelper;
import consulo.webService.util.PropertySet;

/**
 * @author VISTALL
 * @since 28-Aug-16
 */
@Service
public class UserConfigurationService
{
	private static final Logger LOGGER = LoggerFactory.getLogger(PluginAnalyzerService.class);

	private final PluginChannelService[] myPluginChannelServices;

	private final File myConsuloWebServiceHome;

	private File myTempUploadDirectory;

	private AtomicLong myTempCount = new AtomicLong();

	private Executor myExecutor = Executors.newFixedThreadPool(Integer.MAX_VALUE, new ThreadFactory()
	{
		private final ThreadGroup ourGroup = new ThreadGroup("async delete");

		{
			ourGroup.setMaxPriority(Thread.MIN_PRIORITY);
		}

		@NotNull
		@Override
		public Thread newThread(@NotNull Runnable r)
		{
			return new Thread(ourGroup, r);
		}
	});

	private PropertySet myPropertySet;

	public UserConfigurationService()
	{
		this(SystemProperties.getUserHome());
	}

	@VisibleForTesting
	public UserConfigurationService(String userHome)
	{
		ConsuloHelper.init();

		PluginChannel[] values = PluginChannel.values();
		myPluginChannelServices = new PluginChannelService[values.length];
		for(int i = 0; i < values.length; i++)
		{
			myPluginChannelServices[i] = new PluginChannelService(values[i]);
		}

		myConsuloWebServiceHome = new File(userHome, ".consuloWebservice");

		System.setProperty(PathManager.PROPERTY_HOME_PATH, myConsuloWebServiceHome.getPath());
	}

	public PropertySet getPropertySet()
	{
		return myPropertySet;
	}

	public void setProperties(Properties properties)
	{
		File file = new File(myConsuloWebServiceHome, "config.xml");
		FileSystemUtils.deleteRecursively(file);

		try (FileOutputStream fileOutputStream = new FileOutputStream(file))
		{
			properties.storeToXML(fileOutputStream, "hub.consulo.io");

			reloadProperties();
		}
		catch(IOException e)
		{
			LOGGER.error(e.getMessage(), e);
		}
	}

	private void reloadProperties()
	{
		File file = new File(myConsuloWebServiceHome, "config.xml");
		if(file.exists())
		{
			Properties properties = new Properties();
			try
			{
				properties.loadFromXML(new FileInputStream(file));
				myPropertySet = new PropertySet(properties);
			}
			catch(Exception e)
			{
				LOGGER.error(e.getMessage(), e);
			}
		}
	}

	@NotNull
	public PluginChannelService getRepositoryByChannel(@NotNull PluginChannel channel)
	{
		return myPluginChannelServices[channel.ordinal()];
	}

	@NotNull
	public File createTempFile(String prefix, @Nullable String ext)
	{
		long l = myTempCount.incrementAndGet();

		File file = new File(myTempUploadDirectory, StringUtil.isEmpty(ext) ? prefix + "_" + l : prefix + "_" + l + "." + ext);
		if(file.exists())
		{
			FileSystemUtils.deleteRecursively(file);
		}

		return file;
	}

	public void asyncDelete(File... files)
	{
		if(files.length == 0)
		{
			return;
		}
		myExecutor.execute(() -> {
			for(File file : files)
			{
				FileSystemUtils.deleteRecursively(file);
			}
		});
	}

	@NotNull
	public File getConsuloWebServiceHome()
	{
		return myConsuloWebServiceHome;
	}

	@PostConstruct
	public void contextInitialized()
	{
		FileUtilRt.createDirectory(myConsuloWebServiceHome);

		myTempUploadDirectory = new File(myConsuloWebServiceHome, "tempUpload");
		FileSystemUtils.deleteRecursively(myTempUploadDirectory);
		FileUtilRt.createDirectory(myTempUploadDirectory);

		File pluginChannelDir = new File(myConsuloWebServiceHome, "plugin");
		FileUtilRt.createDirectory(pluginChannelDir);

		reloadProperties();

		for(PluginChannelService service : myPluginChannelServices)
		{
			service.initImpl(pluginChannelDir);
		}
	}
}
