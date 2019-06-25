import { compile, compileFromFile } from 'json-schema-to-typescript'

// compile from file
compileFromFile('foo.json')
  .then(ts => fs.writeFileSync('foo.d.ts', ts))
