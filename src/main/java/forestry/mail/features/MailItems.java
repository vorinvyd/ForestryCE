package forestry.mail.features;

import forestry.api.modules.ForestryModuleIds;
import forestry.mail.items.EnumStampDefinition;
import forestry.mail.items.CatalogueItem;
import forestry.mail.items.LetterItem;
import forestry.mail.items.ItemStamp;
import forestry.modules.features.*;

@FeatureProvider
public class MailItems {
	private static final IFeatureRegistry REGISTRY = ModFeatureRegistry.get(ForestryModuleIds.MAIL);

	public static final FeatureItemGroup<ItemStamp, EnumStampDefinition> STAMPS = REGISTRY.itemGroup(ItemStamp::new, "stamp", EnumStampDefinition.VALUES);
	public static final FeatureItemTable<LetterItem, LetterItem.Size, LetterItem.State> LETTERS = REGISTRY.itemTable(LetterItem::new, LetterItem.Size.values(), LetterItem.State.values(), "letter");
	public static final FeatureItem<CatalogueItem> CATALOGUE = REGISTRY.item(CatalogueItem::new, "catalog");
}
