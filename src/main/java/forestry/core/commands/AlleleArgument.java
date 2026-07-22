package forestry.core.commands;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import forestry.api.genetics.ISpeciesType;

/**
 * Parses the raw token identifying an allele value. Alleles are no longer interned in a global registry, so the token
 * cannot be resolved here (the target chromosome is not yet known). The consuming command matches this token against
 * the chromosome's known allele values (see {@code GeneticsUtil.alleleKey}).
 */
public record AlleleArgument(ISpeciesType<?, ?> type) implements ISpeciesArgumentType<String> {
	@Override
	public String parse(StringReader reader) throws CommandSyntaxException {
		int start = reader.getCursor();
		while (reader.canRead() && reader.peek() != ' ') {
			reader.skip();
		}
		return reader.getString().substring(start, reader.getCursor());
	}
}
