package forestry.api.lepidopterology;

import forestry.api.core.IProduct;

import java.util.List;

public interface IButterflyCocoon {
	/**
	 * @return Whether the allele for this value is dominant or recessive.
	 */
	boolean isDominant();

	List<IProduct> getProducts();
}
