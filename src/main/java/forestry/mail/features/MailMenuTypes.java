package forestry.mail.features;

import forestry.api.modules.ForestryModuleIds;
import forestry.mail.gui.*;
import forestry.modules.features.FeatureMenuType;
import forestry.modules.features.FeatureProvider;
import forestry.modules.features.IFeatureRegistry;
import forestry.modules.features.ModFeatureRegistry;

@FeatureProvider
public class MailMenuTypes {
	private static final IFeatureRegistry REGISTRY = ModFeatureRegistry.get(ForestryModuleIds.MAIL);

	public static final FeatureMenuType<ContainerCatalogue> CATALOGUE = REGISTRY.menuType(ContainerCatalogue::fromNetwork, "catalog");
	public static final FeatureMenuType<LetterMenu> LETTER = REGISTRY.menuType(LetterMenu::fromNetwork, "letter");
	public static final FeatureMenuType<ContainerMailbox> MAILBOX = REGISTRY.menuType(ContainerMailbox::fromNetwork, "mailbox");
	public static final FeatureMenuType<ContainerStampCollector> STAMP_COLLECTOR = REGISTRY.menuType(ContainerStampCollector::fromNetwork, "stamp_collector");
	public static final FeatureMenuType<ContainerTradeName> TRADE_NAME = REGISTRY.menuType(ContainerTradeName::fromNetwork, "trade_name");
	public static final FeatureMenuType<ContainerTrader> TRADER = REGISTRY.menuType(ContainerTrader::fromNetwork, "trader");
}
