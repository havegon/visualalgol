package visualalgol.ferramenta;

import java.awt.Color;
import java.awt.Point;
import java.awt.event.MouseEvent;
import java.util.Collections;

import visualalgol.casosdeuso.Ator;
import visualalgol.entidades.Comando;
import visualalgol.entidades.InstrucaoGenerica;
import visualalgol.entidades.Linha;

public class ComandoFerramenta extends Ferramenta {
	@Override
	public void mouseClicked(MouseEvent e) {
		InstrucaoGenerica instrucao = getInstrucaoEm(e.getX(), e.getY());
		if(instrucao!=null) return;
		// pegar a linha em x y do mouse
		Linha linhaEntrada = getLinhaEm(e.getX(), e.getY());
		if(linhaEntrada!=null){
			if(linhaEntrada.getPontoTemporario()!=null){//Criar antes deste ponto
				//separar a linha em duas, quebrando no ponto temporario
				Linha linhaSaida = new Linha();
				boolean copiar = false;
				int x=e.getX();
				
				System.out.println("linhaEntrada.getListPontos().size() = " + linhaEntrada.getListPontos().size());
				
				//for(int i=0;i<linhaEntrada.getListPontos().size();i++){
				for(int i=linhaEntrada.getListPontos().size()-1;i>=0;i--){
					Point point = linhaEntrada.getListPontos().get(i);
					if(!linhaEntrada.getListPontos().remove(point)) throw new RuntimeException("Nao removido");
					linhaSaida.getListPontos().add(point);
					if(point==linhaEntrada.getPontoTemporario()){
						copiar = true;
						x = point.x;
						break;
					}
					System.out.println("i="+i);
				}
				Collections.reverse(linhaSaida.getListPontos());
				//criar o comando
				Comando comando = criarComando(x,e.getY());
				
				//Ligar as linhas
				ligarLinhas(linhaEntrada, linhaSaida, comando);
			}else{//criar antes do destino
				int x = linhaEntrada.getDestino().getX();
				//criar o comando
				Comando comando = criarComando(x,e.getY());
				Linha linhaSaida = new Linha();
				
				//Ligar as linhas
				ligarLinhas(linhaEntrada, linhaSaida, comando);
			}
			Ator.getInstance().criouInstrucao();
		}
	}

	private void ligarLinhas(Linha linhaEntrada, Linha linhaSaida, Comando comando) {
		//a saida deve ser a entrada do destino
		InstrucaoGenerica destino = linhaEntrada.getDestino();
		destino.substituirEntrada(linhaEntrada,linhaSaida);
		linhaSaida.setOrigem(comando);
		linhaSaida.setDestino(destino);
		
		linhaEntrada.setDestino(comando);
		
		comando.setLinhaEntrada(linhaEntrada);
		comando.setLinhaSaida(linhaSaida);
		getAlgoritmo().getListLinha().add(linhaSaida);
	}
	
	private Comando criarComando(int x,int y){
		Comando comando = new Comando();
		comando.setX(x);
		comando.setY(y);
		comando.setW(100);
		comando.setH(40);
		comando.setCor(new Color(0xf0, 0xff, 0xf0).getRGB());
		getAlgoritmo().getListComando().add(comando);
		comando.setAlgoritmo(getAlgoritmo());
		setArrastando(comando);
		return comando;
	}
	
	private void implementacaoAntiga(MouseEvent e){
		if (getInstrucaoEm(e.getX(), e.getY()) == null) {
			Comando comando = new Comando();
			comando.setX(e.getX());
			comando.setY(e.getY());
			comando.setW(100);
			comando.setH(40);
			comando.setCor(new Color(0xf0, 0xff, 0xf0).getRGB());
			getAlgoritmo().getListComando().add(comando);
			comando.setAlgoritmo(getAlgoritmo());
			setArrastando(comando);
		}
	}
}
