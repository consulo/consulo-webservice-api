package consulo.webservice;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.Properties;

import org.jetbrains.annotations.NotNull;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.springframework.util.FileSystemUtils;
import com.intellij.openapi.util.io.FileUtil;
import consulo.webService.UserConfigurationService;
import consulo.webService.plugins.PluginAnalyzerService;
import consulo.webService.plugins.PluginChannel;
import consulo.webService.plugins.PluginChannelIteration;
import consulo.webService.plugins.PluginChannelService;
import consulo.webService.plugins.PluginDeployService;
import consulo.webService.plugins.PluginNode;
import consulo.webService.util.PropertyKeys;

/**
 * @author VISTALL
 * @since 04-Jan-17
 */
public class PluginIterationTest extends Assert
{
	private PluginDeployService myDeployService;
	private PluginChannelIteration myPluginChannelIteration;
	private UserConfigurationService myUserConfigurationService;

	private File myTempDir;

	@Before
	public void before() throws Exception
	{
		myTempDir = FileUtil.createTempDirectory("webService", null);

		FileSystemUtils.deleteRecursively(myTempDir);

		String canonicalPath = myTempDir.getCanonicalPath();

		myUserConfigurationService = new UserConfigurationService(canonicalPath, Runnable::run);
		Properties properties = new Properties();
		properties.setProperty(PropertyKeys.WORKING_DIRECTORY, canonicalPath);

		myUserConfigurationService.setProperties(properties);

		PluginAnalyzerService pluginAnalyzerService = new PluginAnalyzerService(myUserConfigurationService);

		myDeployService = new PluginDeployService(myUserConfigurationService, pluginAnalyzerService);

		myPluginChannelIteration = new PluginChannelIteration(myUserConfigurationService, myDeployService);

		myUserConfigurationService.contextInitialized();
	}

	@After
	public void after() throws Exception
	{
		FileSystemUtils.deleteRecursively(myTempDir);
	}

	@Test
	public void testPlatformIteration() throws Exception
	{
		PluginNode platformNode = deployPlatform(PluginChannel.nightly, 1554, "consulo-win-no-jre", "/consulo-win-no-jre_1554.tar.gz");

		myPluginChannelIteration.iterate(PluginChannel.nightly, PluginChannel.alpha);

		PluginChannelService pluginChannelService = myUserConfigurationService.getRepositoryByChannel(PluginChannel.alpha);

		PluginNode pluginNodeInAlpha = pluginChannelService.select(platformNode.platformVersion, platformNode.id, false);
		assertNotNull(pluginNodeInAlpha);
		assertEquals(pluginNodeInAlpha.id, platformNode.id);
		assertNotNull(pluginNodeInAlpha);
		assertNotNull(pluginNodeInAlpha.targetFile);
		assertTrue(pluginNodeInAlpha.targetFile.exists());
	}

	@NotNull
	private PluginNode deployPlatform(PluginChannel channel, int platformVersion, String pluginId, String pluginPath) throws Exception
	{
		InputStream resourceAsStream = AnalyzerTest.class.getResourceAsStream(pluginPath);

		File tempFile = File.createTempFile("platformTemp", ".tar.gz");
		try (FileOutputStream outputStream = new FileOutputStream(tempFile))
		{
			FileUtil.copy(resourceAsStream, outputStream);
		}

		PluginNode pluginNode = myDeployService.deployPlatform(channel, platformVersion, pluginId, tempFile);

		tempFile.delete();

		return pluginNode;
	}

	@Test
	public void testPluginIteration() throws Exception
	{
		PluginNode pluginNode = deployPlugin(PluginChannel.nightly, "/com.intellij.xml_108.zip");

		myPluginChannelIteration.iterate(PluginChannel.nightly, PluginChannel.alpha);

		PluginChannelService pluginChannelService = myUserConfigurationService.getRepositoryByChannel(PluginChannel.alpha);

		PluginNode pluginNodeInAlpha = pluginChannelService.select(pluginNode.platformVersion, pluginNode.id, false);
		assertNotNull(pluginNodeInAlpha);
		assertEquals(pluginNodeInAlpha.id, pluginNode.id);
		assertNotNull(pluginNodeInAlpha);
		assertNotNull(pluginNodeInAlpha.targetFile);
		assertTrue(pluginNodeInAlpha.targetFile.exists());
	}

	private PluginNode deployPlugin(PluginChannel channel, String... pluginPaths) throws Exception
	{
		PluginNode lastNode = null;
		for(String pluginPath : pluginPaths)
		{
			InputStream resourceAsStream = AnalyzerTest.class.getResourceAsStream(pluginPath);

			lastNode = myDeployService.deployPlugin(channel, () -> resourceAsStream);
		}
		return lastNode;
	}
}