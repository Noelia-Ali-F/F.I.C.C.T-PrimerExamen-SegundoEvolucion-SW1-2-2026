const fs=require('node:fs');const path=require('node:path');const ts=require('typescript');
const root=path.resolve(__dirname,'../src/app');let checked=0;const failures=[];
function walk(dir){return fs.readdirSync(dir,{withFileTypes:true}).flatMap(e=>e.isDirectory()?walk(path.join(dir,e.name)):[path.join(dir,e.name)]);}
for(const layer of ['domain','application'])for(const file of walk(path.join(root,layer)).filter(p=>p.endsWith('.ts')&&!p.endsWith('.spec.ts'))){
 checked++;const ast=ts.createSourceFile(file,fs.readFileSync(file,'utf8'),ts.ScriptTarget.Latest,true);
 for(const statement of ast.statements)if(ts.isImportDeclaration(statement)){
  const spec=statement.moduleSpecifier.text;
  if(spec.startsWith('.')){const target=path.resolve(path.dirname(file),spec);const allowed=layer==='domain'?['domain']:['domain','application'];if(!allowed.some(name=>target.startsWith(path.join(root,name)+path.sep)))failures.push(`${file}: dependencia hacia ${spec}`);}
  else if(!(layer==='application'&&spec==='rxjs'))failures.push(`${file}: dependencia externa ${spec}`);
 }
 function inspect(node){if(ts.isIdentifier(node)&&['window','document','localStorage','sessionStorage','FormData','DOMParser','HttpClient','WebSocket','SpeechRecognition'].includes(node.text))failures.push(`${file}: plataforma ${node.text} dentro del núcleo`);ts.forEachChild(node,inspect);}inspect(ast);
}
if(failures.length){console.error(failures.join('\n'));process.exit(1);}console.log(`Arquitectura hexagonal web: ${checked} archivos de dominio/aplicación sin dependencias de Angular ni adaptadores.`);
