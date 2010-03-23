package visualalgol.casosdeuso;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.mozilla.javascript.Scriptable;

import visualalgol.entidades.Comando;
import visualalgol.entidades.CondicaoIf;
import visualalgol.entidades.InstrucaoGenerica;
import visualalgol.utils.LogSimples;

public class InterpretarWhy extends CasoDeUso{
	private String textoDigitado;
	private boolean encontrado = false;
	private String nomeVariavel;
	private String valor;
	private LogSimples logSimples;
	private static InterpretarWhy instance = new InterpretarWhy();
	private List<InstrucaoGenerica> executados = new ArrayList<InstrucaoGenerica>();
	private InterpretarWhy(){
		logSimples = new LogSimples();
		File file = new File(getPastaDoPrograma(),"log-interpretacao.js");
		logSimples.setPath(file);
	}
	
	public static InterpretarWhy getInstance() {
		return instance;
	}
	
	@Override
	public void executarComoThread() throws InterruptedException {
		if(!textoDigitado.startsWith("why ")){
			return;
		}
		textoDigitado = textoDigitado.replaceAll("=+","=");
		textoDigitado = textoDigitado.replace("?", " ? ");
		textoDigitado = textoDigitado.replace("=", " = ");
		textoDigitado = textoDigitado.replaceAll("\\s+"," ");
		String args[] = textoDigitado.split("\\s"); 
		nomeVariavel =  args[1];
		valor = args[3];
		System.out.println("Procurando o momento em que a variavel " + nomeVariavel + " fica com o valor " + valor);
		
		for (int i = 0; i < executados.size(); i++) {
			InstrucaoGenerica instrucao = executados.get(i);
			int pos = instrucao.contemVariavel(nomeVariavel,valor);
			if(pos!=-1){
				// contem a variavel
				// procurar o proximo if
				for(int j=i-1;j>=0;j--){
					InstrucaoGenerica aux = executados.get(j);
					if(aux instanceof CondicaoIf){
						CondicaoIf condicao = (CondicaoIf) aux;
						sistema.getConsole().write("because " + condicao.getPseudoCodigo() + " is " + condicao.isResultado());
						encontrado = true;
						break;
					}
				}
			}
			if(encontrado) break;
		}
		
		if(!encontrado){
			sistema.getConsole().write("I don't know. Sorry...");
		}
	}
	
	public void setTextoDigitado(String textoDigitado) {
		this.textoDigitado = textoDigitado;
	}

	public void informarComandoExecutado(InstrucaoGenerica instrucao, Scriptable scope, String s) {
		//procurar por alteracoes de variavel
		if(instrucao instanceof Comando){
			if(s!=null){
				int iIgual = s.indexOf("=");
				if(iIgual!=-1){
					String varName = s.substring(0,iIgual);
					Object value = scope.get(varName, scope);
					instrucao.put(varName, value.toString());
				}
			}
		}
		executados.add(instrucao);
	}

	/**
	 * Chamado do aspecto
	 * @param name
	 * @param oldValue
	 * @param newValue
	 */
	public void informarVariavelAlterada(String name, Object oldValue, Object newValue) {
		logSimples.append(name+" = "+newValue+"; //"+oldValue+"\n");
	}

	public void apagarLog() {
		logSimples.apagar();
		executados.clear();
	}
	
}