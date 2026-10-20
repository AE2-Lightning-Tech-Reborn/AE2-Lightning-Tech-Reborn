# Third-Party Notices

## RecipeSearch / Fast Recipe Search

The candidate-index design is inspired by **nutant233 (nutant / mutan)** and
[GregTech-Odyssey/RecipeSearch](https://github.com/GregTech-Odyssey/RecipeSearch),
reviewed at `3c4360aad469315f2d612d04cbd40164f5e2f67b`, and GTO's RecipeDB integration.
[Fast Recipe Search](https://github.com/nutant233/Fast-Recipe-Search) is the related mod.

AE2LT independently implements definition-keyed candidate indexes, mutation-driven
invalidation and full final validation. It retains the original candidate order.
No upstream source or trie library is copied or embedded. Attribution is also
included in the JAR at `META-INF/recipesearch-attribution.txt`.

## ExtendedAE Plus [ClientPlus]

The JEI/EMI recipe-transfer hooks and recipe keyword priority model in this project include
adapted portions of ExtendedAE Plus [ClientPlus]. Automatic encoding is intentionally not included.

- Upstream repository: https://github.com/gjmhmm8/ExtendedAE-Plus_ClientPlus
- Revision reviewed and adapted: `07f8373c590c0c6d845f794e7c25090e5ef5703e`
- Upstream author attribution: XianYuFish001 / gjmhmm8
- Upstream license: GNU Lesser General Public License version 3

The adapted implementation has been scoped to the Tianshu pattern terminal and retains
AE2 Lightning Tech's server-authoritative upload acknowledgement and target validation.
The complete GNU LGPL 3.0 license text is distributed in `META-INF/LICENSE` inside the mod JAR
and in the repository root `LICENSE` file.
