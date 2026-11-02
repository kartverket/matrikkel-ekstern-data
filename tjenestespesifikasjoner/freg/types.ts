const data: string = await Bun.file('openapi.yaml').text();
const spec = Bun.YAML.parse(data) as Spec;

type Spec = {
    openapi: '3.0.1';
    info: {};
    paths: {
        [key: string]: Operation
    };
    components: {
        schemas: {
            [key: string]: Component
        }
    }
}

type Operation = {}
type Component = {};

type RewriteRule = {
    prefix: string;
    replacement: string,
}

const componentNames = Object.keys(spec.components.schemas);

const rewriteComponentRules: RewriteRule[] = [
    {
        prefix: 'folkeregisteret.tilgjengeliggjoering.hendelse.v1.',
        replacement: 'HendelseV1.'
    },
    {
        prefix: 'folkeregisteret.tilgjengeliggjoering.person.v1.',
        replacement: 'PersonV1.'
    },
    {
        prefix: 'folkeregisteret.tilgjengeliggjoering.uttrekk.v1.',
        replacement: 'UttrekkV1.'
    },
    { prefix: 'ske.folkeregister.exception.javaxrs.', replacement: '' },
    { prefix: 'ske.folkeregister.lesemodell.universal.feil.', replacement: '' },
    { prefix: 'ske.folkeregister.rettighetspakke.hendelser.', replacement: '' },
];

function rewrite(componentName: string): string {
    const rule = rewriteComponentRules.find((rule) => {
        return componentName.startsWith(rule.prefix);
    });
    if (!rule) return componentName;

    return componentName.replace(rule.prefix, rule.replacement);
}

let fixedData = data;
for (const componentName of componentNames) {
    console.log('Fixing', componentName);
    const replacement = rewrite(componentName);
    fixedData = fixedData.replaceAll(componentName, replacement)
}

await Bun.write('openapi-fixed.yaml', fixedData);
